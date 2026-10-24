# DECISOES.md — FilaVIVA

## A abordagem em uma frase

Um **framework clínico explícito (SWALIS adaptado)** decide a urgência; um **score explicável** ordena dentro da mesma urgência; a fila é **recalculada a cada evento novo**; e o **regulador tem a última palavra**, sempre com justificativa registrada.

## Para quem e quando

- **Usuário principal: o regulador da fila cirúrgica.** É quem já decide hoje e quem responde pela decisão. Desenhamos para ajudá-lo a decidir melhor, não para decidir por ele.
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
5. **Trava da regra de ouro**: depois de ordenar, nenhum CID C* pode ficar abaixo de um paciente sem urgência clínica. Usamos **exatamente a mesma definição conservadora do verificador oficial**. A trava faz o menor ajuste possível: quem é "sem urgência" pela definição oficial e estava acima de algum oncológico desce para logo depois do último oncológico, e quem tem urgência não sai do lugar. Hoje isso afeta 2 pacientes (com lesão de tronco de coronária, dor baixa e nenhum evento), marcados na tela e na lista de reavaliação. A checagem roda a cada recálculo; se falhar, o sistema para em vez de mostrar uma fila insegura.

## Decisões difíceis

- **O tempo de espera nunca passa por cima da urgência clínica.** Um paciente classe D esperando 4 anos não fica à frente de um câncer esperando 3 meses. O tempo pesa dentro da classe (15 pontos) e alimenta a lista de **esquecidos** (mais que o dobro do prazo e prioridade nunca revisada), que vai para reavaliação humana. Preferimos tornar o esquecido visível para o regulador a deixar o relógio reordenar a fila sozinho.
- **Biópsia positiva vale mais que o CID do cadastro.** 23 pacientes têm laudo de malignidade e CID benigno ou incerto. Para nós eles são oncológicos (A2), e o sistema avisa que o cadastro está errado. Exemplo: `SOL-2026-00351` (CID D44.0, prioridade "baixa", laudo "NEOPLASIA MALIGNA") sai da posição 866 para a 241.
- **Usamos o CID, não a flag.** 44 flags oncológicas não batem com o CID. A flag não entra em nenhuma regra; só gera aviso de "cadastro com flag errada".
- **Dado faltando não rebaixa.** Sem dor ou limitação informadas, o paciente fica no mínimo em C e vai para a lista de reavaliação. Não colocamos ninguém no fim da fila por falta de informação.
- **Oncológico nunca é rebaixado, nem pelo regulador.** O regulador pode promover qualquer um e rebaixar quem não tem câncer, sempre com justificativa. Tentar colocar um câncer abaixo de A2 é recusado com mensagem clara: se o diagnóstico mudou, registre a nova biópsia.
- **Ninguém sai da fila sem decisão humana.** Desistência, cirurgia em outro serviço ou "operou pelo convênio" (em eventos ou observações) viram **saída sugerida**: o paciente **continua na posição calculada**, com um selo na tela e o aviso na justificativa, e entra na lista "Confirmar saída" para o regulador. Só sai quando o regulador confirma; se recusar, o aviso some. Isso vale também para quem tem câncer. O custo é que um paciente que talvez já tenha operado pode ocupar uma vaga alta (3 dos 24 estão em A1); por isso a confirmação aparece em destaque na revisão diária. Decisão a validar com o mentor.
- **Texto livre com cuidado clínico.** Revisando todos os alarmes caso a caso, achamos dois erros e corrigimos, cada um com teste:
  - "septic" casava dentro de "asséptica" (necrose asséptica é justamente sem infecção) e colocava 7 pacientes em A1 por "sepse";
  - "CATE: TRONCO 80%" não era reconhecido como lesão de tronco de coronária, e 3 pacientes com essa lesão estavam em C ou D. Agora lesão de tronco é alarme grave (A1), e doença coronária triarterial ou multiarterial, angina CCS III/IV e fração de ejeção abaixo de 40% levam no mínimo a B. Os cortes estão em [docs/VALIDACAO-MENTOR.md](docs/VALIDACAO-MENTOR.md) para validar.
- **Negação no texto.** "Sem sinais de encarceramento" não é encarceramento. O leitor de texto ignora termos negados na mesma frase. A trava da regra de ouro, de propósito, **não** trata negação (é mais conservadora, como o verificador).
- **Sem LLM no caminho da decisão.** As regras ficam em `backend/src/main/resources/regras.json`, legíveis e auditáveis por um médico. Uma decisão sobre quem opera primeiro precisa ser reproduzível: a mesma entrada dá sempre a mesma fila.

## Como o `fila_priorizada.csv` está montado (919 de 941)

O arquivo traz **todos os 919 pacientes da fila, na ordem do FilaVIVA**. As 24 **saídas sugeridas** (desistiu, operou em outro serviço ou pelo convênio) estão na posição calculada, com a justificativa terminando em "saída sugerida aguardando o regulador (motivo)". Distribuição: A1 146, A2 142, B 224, C 205, D 202.

**Ficaram fora só as 22 solicitações duplicadas** (mesmo paciente e mesmo procedimento). Mesclamos na de entrada mais antiga, porque o paciente espera desde lá; eventos e observações da duplicata passam para a principal. 5 delas são oncológicas (`SOL-2023-00088`, `SOL-2026-00865`, `SOL-2026-00864`, `SOL-2026-01014`, `SOL-2026-00816`) e **a principal de cada uma está no arquivo**, com o mesmo CID. Se uma duplicata tivesse CID de câncer e a principal não, o CID de câncer passaria para a principal.

O arquivo passa no `verificar_fila_priorizada.py`: regra de ouro respeitada, 100% dos oncológicos no primeiro terço, 94% dos pacientes com PS ou internação recente na primeira metade e 93% dos sem urgência no último terço. O `scripts/conferir_csv.py` confere também a cobertura (cada solicitação fora do arquivo é uma duplicata com a principal dentro), as justificativas e a coerência das classes.

## Como validamos

- `backend`: 18 testes automáticos (motor e dataset completo).
- `scripts/teste_integracao.py`: 35 verificações contra o sistema real (Postgres, backend e frontend no ar), passando pelos fluxos da demo, e reinicia a demo no fim.
- `scripts/conferir_csv.py` e o verificador oficial sobre o CSV final.
- Classificação a revisar com o mentor clínico usando [docs/VALIDACAO-MENTOR.md](docs/VALIDACAO-MENTOR.md) (regras, contagens, casos reais e perguntas).
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

- **Agenda de sala, preparo pré-operatório e cancelamentos**: fora do escopo do desafio. A HbA1c acima de 8,5% aparece só como alerta de "descompensação: encaminhar para controle", sem mexer na urgência cirúrgica.
- **Machine learning**: não há rótulo de "ordem certa" para treinar, e a banca pede decisões explicáveis. Regras explícitas são auditáveis.
- **Login e perfis de usuário**: o campo "autor" registra quem decidiu. Em produção viria do login do hospital.
- **Questionário do paciente ("voz do paciente")**: é o próximo passo natural. A arquitetura já recalcula a fila a cada evento, então uma resposta de questionário seria só mais um tipo de evento.
