# DECISOES.md — FilaVIVA

## A abordagem em uma frase

Um **framework clínico explícito (SWALIS adaptado)** decide a urgência; um **score explicável** ordena dentro da mesma urgência; a fila é **recalculada a cada evento novo**; e o **regulador tem a última palavra**, sempre com justificativa registrada.

## Para quem e quando

- **Usuário principal: o regulador da fila cirúrgica.** É quem já decide hoje e quem responde pela decisão. Desenhamos para ajudá-lo a decidir melhor, não para decidir por ele.
- **Também os médicos, nas listas da especialidade deles.** Cada médico vê os pacientes da especialidade (cirúrgicos e "possível avaliação") e pode mudar a classe ou a posição de um paciente dessas listas, sabendo de algo que ainda não está no sistema. Passa pelas mesmas regras do regulador: subir vale na hora, descer vira sugestão que o regulador confirma, oncológico nunca perde classe, a regra de ouro continua valendo e tudo fica na auditoria com nome, CRM e id do médico. O regulador continua sendo quem fecha a fila.
- **Momento: a cada evento novo e na revisão diária.** Uma biópsia, uma ida ao PS ou uma reavaliação recalcula a fila inteira na hora (cerca de 0,4 s para os 919 pacientes) e gera um alerta com o impacto: "subiu da posição 412 para 18, classe C → A2, por causa da biópsia positiva de hoje". A tela de alertas é a revisão do dia.

## Como a ordem é decidida

1. **Limpeza dos dados** (detalhes abaixo).
2. **Sinais**: cada fato clínico vira um sinal **com a evidência citada**: o trecho do laudo, a frase do médico ou o valor da coluna. Vêm das colunas (CID, dor, limitação, ASA), dos eventos (biópsia, PS, internação, imagem, reavaliação, contato, HbA1c) e do texto livre (justificativa e observações).
3. **Classe SWALIS** (a regra mais alta que bater vence):

| Classe | Prazo | Regra |
|---|---|---|
| A1 | 15 dias | Câncer com sinal de progressão (imagem, PS/internação recente, alarme no texto, reavaliação com piora); ou CID de urgência (ex.: hérnia com obstrução, colecistite aguda); ou alarme grave no texto (encarceramento, sepse, colangite, perda de função…) |
| A2 | 21 dias | CID C* **ou biópsia positiva** (piso de todo paciente com câncer) |
| B | 60 dias | Dor ≥ 7, limitação grave, PS/internação recente, reavaliação com piora, relato de piora, lesão incerta (D37–D48, R91), sinais de malignidade ou perda funcional no texto |
| C | 180 dias | Dor 4–6 ou limitação moderada; ou dor/limitação **não informadas** |
| D | 360 dias | O resto |

4. **Dentro da classe**, o score de 0–100 ordena. Ele tem 6 componentes, cada um explicado na tela: diagnóstico 30, piora 25, dor 15, limitação 10, tempo de espera relativo ao prazo 15, ASA/risco cirúrgico 5. O regulador pode testar outros pesos no simulador.
5. **Trava da regra de ouro**: depois de ordenar, como **restrição explícita** (não como pontos no score), nenhum paciente protegido pode ficar abaixo de um paciente sem urgência clínica. A trava faz o menor ajuste possível: quem é "sem urgência" e estava acima do último protegido desce para logo depois dele; quem tem urgência não sai do lugar. Quem é "sem urgência" e quem é protegido depende da fila (veja "Duas filas" abaixo). A checagem roda a cada recálculo, nas duas filas; se falhar, o sistema para em vez de mostrar uma fila insegura.

## Duas filas: operacional e de referência

O verificador oficial (`verificar_fila_priorizada.py`) decide quem é "sem urgência" lendo **só o dataset de referência**. Ele não enxerga eventos registrados depois, nem urgências que só aparecem na nossa leitura do texto do médico. A tela precisa reagir a esses eventos novos, e o arquivo de entrega precisa ser reproduzível e julgado só pelo dataset. Por isso existem **duas filas, sempre identificadas**:

| | **Fila operacional** | **Fila de referência — entrega oficial** |
|---|---|---|
| Para quem | O regulador, na tela e na API | A banca: gera o `fila_priorizada.csv` |
| Dados | Dataset + eventos e decisões registrados | Só o dataset de referência, sem eventos novos nem decisões |
| Quem é "sem urgência" | Classe clínica calculada D, com dor e limitação **informadas**, e nenhum critério de urgência do verificador em **todos** os eventos (inclusive os novos). Dado ausente nunca conta como calmo | Exatamente o verificador: só eventos do dataset, dado ausente tratado como "nenhum" |
| Quem é protegido | CID C* **ou biópsia positiva** | CID C* (a regra oficial) |
| Recalcula | A cada evento ou decisão | Uma vez; é reproduzível (versão = hash dos CSVs do dataset e do `regras.json`) |
| Identificação | `operacional`, versão `op-N`, horário | `oficial`, versão `ref-<hash>`, horário |
| Passa no verificador? | Sim sobre o dataset. Com eventos novos, o verificador não os enxerga; a regra vale pelos mesmos critérios sobre todos os eventos (ver abaixo) | **Sim** |

- **Uma promoção manual só passa à frente de protegidos com evidência de agravamento registrada.** A classe calculada vem das regras clínicas, não do ajuste do regulador.
  - Promover um paciente acima da classe calculada registra a decisão, com justificativa e autor na auditoria. Mas, se não houver evidência de agravamento, ele fica logo abaixo de todos os protegidos, com o selo "posição limitada pela regra de ouro" e o pedido para registrar o atendimento ou exame que justifica a urgência. Isso vale também para quem tem dor moderada ou dado faltando.
  - **Conta como evidência:** PS ou internação recente, progressão em imagem, reavaliação com piora, alarme grave ou funcional, doença grave descrita, CID de urgência e sinais de malignidade.
  - **Não conta como evidência:** dor, limitação, dado faltando, relato de piora (geralmente por telefone) ou um relato grave ainda não confirmado.
  - Quando a promoção passa, a justificativa cita a evidência e a fonte (ex.: "evidência da promoção: Pronto-socorro em 23/10/2026 (evento EVT-…)").
- **Evidência estruturada nova reclassifica; texto livre não.** Uma progressão em imagem, uma internação ou uma biópsia positiva registradas agora (tipo e resultado do evento) reclassificam o paciente e recalculam score e posição. Para o texto livre, a política está em "Texto livre: só alerta e evidência" abaixo.

## Texto livre: só alerta e evidência

Política conservadora, adotada ao estabilizar o motor:

- **Texto novo (descrição de eventos registrados depois da importação) nunca muda a classe.** Uma menção **afirmativa** de alarme grave ("sepse", "hérnia encarcerada"…) gera "possível agravamento grave, a confirmar". Isso abre uma **REVISÃO CLÍNICA URGENTE** (primeiro grupo da lista de reavaliação) e dá um pequeno ganho de score dentro da própria classe. Menções **negadas** ("sem sinais de", "afastada"), **hipotéticas** ("risco de", "suspeita de", "a descartar", "orientado sobre") e **históricas** ("história de", "há 5 anos", "em 2019", "prévia") são ignoradas. A classe vem do evento estruturado: uma internação leva a B.
- **Texto não serve, sozinho, para promover à frente de cânceres.** O regulador pode promover com base no texto, mas precisa haver um atendimento ou exame estruturado registrado (PS, internação, imagem, reavaliação). Esse registro aparece na justificativa como evidência da promoção.
- **Texto do cadastro (justificativa do médico e observações do dataset de referência): classificação mantida, mas marcada como provisória.** Hoje, 85 pacientes têm a classe definida pelo texto. Para eles:
  - Rebaixar automaticamente seria o texto (ou a falta dele) provocando rebaixamento. Por isso **a classe e a posição foram mantidas** e a justificativa diz "classe provisória: urgência descrita só no texto, pendente de revisão clínica (dados estruturados: X)".
  - Quando a classe provisória é A1 ou A2, o paciente entra em **REVISÃO CLÍNICA URGENTE**, com o trecho original e a fonte, e o aviso de que **não é confirmação médica**.
  - Entre eles estão os **12 pacientes cuja urgência está só no texto e que cairiam de A1 para D** se o texto fosse ignorado:
    - 2 com hérnia encarcerada reduzida no PS;
    - 2 com retenção urinária aguda;
    - 3 com túnel do carpo com atrofia;
    - colangite, pielonefrite obstrutiva, colelitíase;
    - 2 com "TRONCO 80%".
  - Menções hipotéticas ou históricas nesse texto são marcadas como tal (20 justificativas); ainda contam para a classe provisória, pela mesma razão.
- **Limitação assumida:** para esses 85, o texto do dataset continua definindo a classe até a revisão humana. Não há como manter a posição sem usar a classe que o texto indica; inventar uma faixa intermediária seria uma regra clínica nova. Preferimos manter, marcar e pedir revisão. A lista está em [docs/VALIDACAO-CLINICA.md](docs/VALIDACAO-CLINICA.md).
- **Ainda é leitura por palavras-chave:** só negação, hipótese e histórico são tratados, e nada disso foi validado por profissional de saúde.
- **Toda resposta da API diz de qual fila é**: cabeçalho `X-FilaViva-Snapshot` (ex.: `operacional; versao=op-12; calculado=…`), campo `snapshot` na resposta da fila e os dois snapshots em `/api/status`.
- **Downloads**: `GET /api/fila/export.csv` entrega por padrão a fila de referência, com o nome `fila_priorizada.csv`. `?snapshot=operacional` entrega a fila da tela, com o nome `fila_operacional_op-N_<horário>.csv`. Com `&versao=op-N`, a API recusa (HTTP 409) se a fila mudou desde que a tela foi carregada; assim o arquivo e a tela nunca divergem em silêncio.
- **A regra de ouro vale sem exceção também na fila da tela.** Uma urgência que só o FilaVIVA lê e que os critérios do verificador oficial não reconhecem não passa à frente de quem tem câncer em nenhuma das duas filas. Os critérios são dor, limitação, CID de urgência, evento de piora e palavra de alarme, olhando todos os eventos, inclusive os novos. Hoje são 2 pacientes com "CATE: TRONCO 80%" (`SOL-2026-00960` e `SOL-2026-00765`): continuam A1, mas ficam logo abaixo do último oncológico, marcados "Urgência que o verificador oficial não reconhece". Se a urgência for real, basta registrar a reavaliação com piora, o atendimento ou o exame, e eles sobem. Antes, na tela, eles seguiam a ordem clínica e ficavam acima de oncológicos. Trocamos isso porque uma leitura de texto ainda não validada não deve passar à frente de câncer. Conferido com o próprio verificador oficial sobre a fila da tela exportada: na fila inicial e depois de 600 ações aleatórias, com os eventos novos acrescentados ao dataset que ele lê. Essa leitura do texto ainda **não foi validada por um profissional de saúde** (ver [docs/VALIDACAO-CLINICA.md](docs/VALIDACAO-CLINICA.md)).
- **Simulações não mexem em nenhuma das duas filas.**

## Decisões difíceis

- **O tempo de espera nunca passa por cima da urgência clínica.** Um paciente classe D esperando 4 anos não fica à frente de um câncer esperando 3 meses. O tempo pesa dentro da classe (15 pontos) e alimenta a lista de **esquecidos** (mais que o dobro do prazo e prioridade nunca revisada), que vai para reavaliação humana. Preferimos tornar o esquecido visível para o regulador a deixar o relógio reordenar a fila sozinho.
- **Biópsia positiva vale mais que o CID do cadastro.** 23 pacientes têm laudo de malignidade e CID benigno ou incerto. Para nós eles são oncológicos (A2), e o sistema avisa que o cadastro está errado. Exemplo: `SOL-2026-00351` (CID D44.0, prioridade "baixa", laudo "NEOPLASIA MALIGNA") sai da posição 866 para a 241.
- **Usamos o CID, não a flag.** 44 flags oncológicas não batem com o CID. A flag não entra em nenhuma regra; só gera aviso de "cadastro com flag errada".
- **Dado faltando não rebaixa.** Sem dor ou limitação informadas, o paciente fica no mínimo em C e vai para a lista de reavaliação. Não colocamos ninguém no fim da fila por falta de informação.
- **Rebaixamento só sugerido; oncológico nunca rebaixado.** A FAQ do desafio diz: "A solução pode rebaixar alguém? Pode sugerir, com justificativa, para o regulador confirmar. Nunca um oncológico." Por isso:
  - Pedir uma classe menos urgente que a vigente (ou um recálculo que a baixaria, por exemplo depois de corrigir um evento) **não rebaixa**: vira "rebaixamento sugerido", com a classe vigente, a proposta, os fatores e as evidências. A classe vigente continua valendo até o regulador **confirmar** ou **rejeitar** na tela de Alertas, com justificativa, autor e horário registrados. Rejeitar mantém a classe e guarda a sugestão no histórico.
  - **Oncológico** (CID C ou biópsia positiva) nunca tem a classe reduzida, nem de A1 para A2, nem por decisão manual, nem por recálculo. O pedido manual é recusado com a explicação; uma queda automática vira "rebaixamento bloqueado" e a classe é mantida.
  - "Confirmar classe" agora mantém a classe vigente. Antes ela voltava para a classe calculada, o que desfazia uma promoção (podia levar um oncológico de A1 para A2).
  - **Queda de posição não é rebaixamento**: quando alguém mais grave passa à frente, a classe do paciente não muda e não há nada a confirmar.
  - **A prioridade manual do cadastro não é tratada como prioridade clínica reconhecida** (o dicionário de dados a descreve como "ruidosa, raramente revisada"). Não existe equivalência oficial entre alta/média/baixa e A1–D. A diferença vira o alerta "Divergência de prioridade — revisão necessária", não um rebaixamento pendente. Sinalizamos só os extremos que a tabela SWALIS do desafio sustenta: classe urgente (A1/A2) sem prioridade "alta" (158) e prioridade "alta" em classe sem dor ou disfunção importantes (C/D, 95). "Alta" em B (70) não é sinalizada: B é "dor, disfunção ou incapacidade importantes".
  - O `fila_priorizada.csv` não muda: ele é a fila de referência, só com o dataset e sem decisões.
- **Separar a fila da tela do arquivo de entrega.** Testamos piorar ao vivo um paciente sem urgência e promovê-lo para A1: ele passou à frente de 126 pacientes com câncer, e o verificador oficial reprovou o CSV exportado, porque só lê o `eventos_clinicos.csv` original. Uma primeira correção fez a trava ignorar eventos novos; ela passava no verificador, mas segurava agravamentos reais (uma biópsia positiva nova ficava atrás de 26 cânceres com score menor). Ficamos com a separação descrita em "Duas filas": a tela reconhece o agravamento e o arquivo de entrega continua reproduzível e aprovado, cada um identificado.
- **Ninguém sai da fila sem decisão humana.** Desistência, cirurgia em outro serviço ou "operou pelo convênio" (em eventos ou observações) viram **saída sugerida**: o paciente **continua na posição calculada**, com um selo na tela e o aviso na justificativa, e entra na lista "Confirmar saída" para o regulador. Só sai quando o regulador confirma; se recusar, o aviso some. Isso vale também para quem tem câncer. O custo é que um paciente que talvez já tenha operado pode ocupar uma vaga alta (3 dos 24 estão em A1); por isso a confirmação aparece em destaque na revisão diária. Decisão a validar com o mentor.
- **Texto livre com cuidado clínico.** Revisando todos os alarmes caso a caso, achamos dois erros e corrigimos, cada um com teste:
  - "septic" casava dentro de "asséptica" (necrose asséptica é justamente sem infecção) e colocava 7 pacientes em A1 por "sepse";
  - "CATE: TRONCO 80%" não era reconhecido como lesão de tronco de coronária, e 3 pacientes com essa lesão estavam em C ou D. Agora lesão de tronco é alarme grave (A1), e doença coronária triarterial ou multiarterial, angina CCS III/IV e fração de ejeção abaixo de 40% levam no mínimo a B. Os cortes estão em [docs/VALIDACAO-MENTOR.md](docs/VALIDACAO-MENTOR.md) para validar.
- **Negação no texto.** "Sem sinais de encarceramento" não é encarceramento. O leitor de texto ignora termos negados na mesma frase. A trava da regra de ouro, de propósito, **não** trata negação (é mais conservadora, como o verificador).
- **Sem LLM no caminho da decisão.** As regras ficam em `backend/src/main/resources/regras.json`, legíveis e auditáveis por um médico. Uma decisão sobre quem opera primeiro precisa ser reproduzível: a mesma entrada dá sempre a mesma fila.

## O que ajuda a decidir sem mexer na ordem

Estas funções informam o regulador e o médico, mas **nunca mudam a classe, o score nem a posição**. Mexer na ordem por saúde geral, vontade ou recursos penalizaria justamente quem é mais doente ou tem menos acesso. Elas marcam, avisam e pedem decisão humana.

| Função | O que faz | Por que não mexe na ordem |
|---|---|---|
| **Desejo do paciente** | Registra se ele quer ser operado (quer, não quer, em dúvida), com quem perguntou e quando | "Não quer" e "em dúvida" viram pendência do regulador. Tirar da fila é uma saída, decidida por pessoa |
| **Conversa BRAN** | Rascunho de Benefícios, Riscos, Alternativas e o que acontece se Nada for feito, por procedimento e com os fatores do próprio paciente; registro da conversa e versão para o paciente | É comunicação, não critério de prioridade |
| **Aptidão cirúrgica** | Parecer (apto, apto com ressalvas, otimizar antes) a partir da HbA1c, ASA, idade, comorbidades e fatores que o médico informa; estimativa de tempo de sala, internação e UTI | Otimizar a saúde antes não é perder o lugar: marca "não agendar" e o próximo é chamado. A equipe decide |
| **Coluna Saúde** | Diabetes, última HbA1c e comorbidades na fila e na lista do médico | Só mostra o que o CSV registra. Diabetes só quando o cadastro diz (comorbidades, `flag_diabetico` ou CID E10–E14): HbA1c alta sozinha aparece como exame, sem diagnóstico |
| **Assistente de IA** | Chat sobre a fila e o uso do sistema, categorização de textos e relatórios para médicos ([docs/AGENTE-IA.md](docs/AGENTE-IA.md)) | **Nunca decide nem altera a fila**: lê números agregados e o que já está calculado; todo relatório sai com "Gerado por IA — revisar antes de usar" |

Toda decisão humana (classe, posição, rebaixamento, saída, aptidão, BRAN, desejo) e todo exame ou evento registrado ficam na trilha de auditoria: quem, quando, por quê e o impacto na fila.

## Como o `fila_priorizada.csv` está montado (919 de 941)

O arquivo é a **fila de referência**: só o dataset, gravado na raiz toda vez que o backend sobe e igual, byte a byte, a cada execução. Traz **todos os 919 pacientes da fila, na ordem do FilaVIVA**. As 24 **saídas sugeridas** (desistiu, operou em outro serviço ou pelo convênio) estão na posição calculada, com a justificativa terminando em "saída sugerida aguardando o regulador (motivo)". Distribuição: A1 146, A2 142, B 224, C 205, D 202.

**Ficaram fora só as 22 solicitações duplicadas** (mesmo paciente e mesmo procedimento). Mesclamos na de entrada mais antiga, porque o paciente espera desde lá; eventos e observações da duplicata passam para a principal. 5 delas são oncológicas (`SOL-2023-00088`, `SOL-2026-00865`, `SOL-2026-00864`, `SOL-2026-01014`, `SOL-2026-00816`) e **a principal de cada uma está no arquivo**, com o mesmo CID. Se uma duplicata tivesse CID de câncer e a principal não, o CID de câncer passaria para a principal.

O arquivo passa no `verificar_fila_priorizada.py`: regra de ouro respeitada, 100% dos oncológicos no primeiro terço, 94% dos pacientes com PS ou internação recente na primeira metade e 93% dos sem urgência no último terço. O `scripts/conferir_csv.py` confere também a cobertura (cada solicitação fora do arquivo é uma duplicata com a principal dentro), as justificativas e a coerência das classes.

## Como validamos

- `backend`: 681 testes automáticos (`./mvnw test`), entre eles 17 sobre rebaixamentos (sugerir, confirmar, rejeitar, queda de posição, oncológico em A1/A2 por decisão e por recálculo, divergência e CSV oficial), os das decisões dos médicos, desejo, BRAN, aptidão, coluna Saúde (comorbidades conferidas contra o CSV nos 919 pacientes) e auditoria de eventos.
- `frontend`: 122 testes de componentes (vitest) e 12 de ponta a ponta no navegador (Playwright), incluindo "o regulador não consegue rebaixar um oncológico abaixo de A2".
  - **Motor:** regra de ouro nas duas filas; promoção com e sem evidência; texto afirmativo, negado, hipotético e histórico (só alerta, nunca muda a classe); classe provisória por texto; dado ausente; biópsia nova; 300 cenários aleatórios.
  - **Regra de ouro sem exceção** (`RegraDeOuroSemExcecaoTest`): critérios do verificador reescritos no teste, independentes do motor, conferidos na fila da tela inicial e depois de 300 ações aleatórias (eventos, classes, rebaixamentos, posições); urgência só no texto fica abaixo dos oncológicos e sobe quando uma reavaliação com piora é registrada.
  - **Dataset completo:** os 12 pacientes com urgência só no texto (classe mantida, em revisão urgente, nas duas filas); regressão do `SOL-2026-00351`; pacientes `SOL-2026-00738`, `SOL-2026-00950`, `SOL-2025-00173` e os de tronco coronário; 40 ações aleatórias sem nenhum paciente sem urgência acima de um CID C, nas duas filas; simulação; igualdade API = CSV operacional; fila de referência igual ao `fila_priorizada.csv`.
- `scripts/teste_integracao.py`: 42 verificações contra o sistema real (Postgres, backend e frontend no ar), passando pelos fluxos da demo e pelas duas filas, e reinicia a demo no fim.
- `scripts/conferir_csv.py` e o verificador oficial sobre o CSV final.
- **Validação clínica: não realizada.** O time não teve acesso a profissional de saúde. As regras, as contagens, os casos reais e as perguntas que um médico ou regulador precisa responder antes de uso real estão em [docs/VALIDACAO-CLINICA.md](docs/VALIDACAO-CLINICA.md).
- Demo ensaiada com [docs/ROTEIRO-DEMO.md](docs/ROTEIRO-DEMO.md).

## Tratamento dos defeitos dos dados

| Defeito | O que fizemos |
|---|---|
| CID sem ponto (32) | Normalizado: maiúsculo e sem ponto para comparar (`K409`); exibido com ponto (`K40.9`) |
| Prioridade com grafia variada (23) ou vazia (11) | Minúscula e sem acento; vazia é tratada como "sem prioridade" |
| Município com caixa errada (50) | Convertido para "Porto Alegre" |
| Solicitações duplicadas (22) | Mescladas na mais antiga (ver acima) |
| Eventos duplicados (7) e órfãos (9) | Guardados no banco, ignorados no cálculo |
| Flags manuais erradas | Ignoradas nas regras; viram aviso de reavaliação |
| Informação só no texto livre | Dicionário de alarmes com negação (`regras.json`) |
| Dor, limitação ou ASA vazios | Nunca rebaixam; viram "dado incompleto" para reavaliação |

## O que deixamos de fora de propósito

- **Agenda de sala, preparo pré-operatório e cancelamentos**: fora do escopo do desafio. A HbA1c acima de 8,5% aparece só como alerta de "descompensação: encaminhar para controle", sem mexer na urgência cirúrgica. A aptidão cirúrgica (acima) é apoio para o preparo, sem entrar na ordem.
- **Machine learning**: não há rótulo de "ordem certa" para treinar, e a banca pede decisões explicáveis. Regras explícitas são auditáveis.
- **Login e perfis de usuário**: o campo "autor" registra quem decidiu. Em produção viria do login do hospital.
- **Questionário do paciente ("voz do paciente")**: é o próximo passo natural. A arquitetura já recalcula a fila a cada evento, então uma resposta de questionário seria só mais um tipo de evento.
