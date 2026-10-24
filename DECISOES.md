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
5. **Trava da regra de ouro**: depois de ordenar, nenhum CID C* pode ficar abaixo do primeiro paciente sem urgência clínica. Usamos **exatamente a mesma definição conservadora do verificador oficial** e rodamos a checagem a cada recálculo. Se falhar, o sistema para em vez de mostrar uma fila insegura.

## Decisões difíceis

- **O tempo de espera nunca passa por cima da urgência clínica.** Um paciente classe D esperando 4 anos não fica à frente de um câncer esperando 3 meses. O tempo pesa dentro da classe (15 pontos) e alimenta a lista de **esquecidos** (mais que o dobro do prazo e prioridade nunca revisada), que vai para reavaliação humana. Preferimos tornar o esquecido visível para o regulador a deixar o relógio reordenar a fila sozinho.
- **Biópsia positiva vale mais que o CID do cadastro.** 23 pacientes têm laudo de malignidade e CID benigno ou incerto. Para nós eles são oncológicos (A2), e o sistema avisa que o cadastro está errado. Exemplo: `SOL-2026-00351` (CID D44.0, prioridade "baixa", laudo "NEOPLASIA MALIGNA") sai da posição 842 para a 243.
- **Usamos o CID, não a flag.** 44 flags oncológicas não batem com o CID. A flag não entra em nenhuma regra; só gera aviso de "cadastro com flag errada".
- **Dado faltando não rebaixa.** Sem dor ou limitação informadas, o paciente fica no mínimo em C e vai para a lista de reavaliação. Não colocamos ninguém no fim da fila por falta de informação.
- **Oncológico nunca é rebaixado, nem pelo regulador.** O regulador pode promover qualquer um e rebaixar quem não tem câncer, sempre com justificativa. Tentar colocar um câncer abaixo de A2 é recusado com mensagem clara: se o diagnóstico mudou, registre a nova biópsia.
- **Saída da fila é sugestão, não exclusão.** Desistência, cirurgia em outro serviço ou "operou pelo convênio" (em eventos ou observações) viram **saída sugerida**, para o regulador confirmar ou recusar. **Paciente com câncer que "desistiu" continua na fila** até alguém confirmar.
- **Negação no texto.** "Sem sinais de encarceramento" não é encarceramento. O leitor de texto ignora termos negados na mesma frase. A trava da regra de ouro, de propósito, **não** trata negação (é mais conservadora, como o verificador).
- **Sem LLM no caminho da decisão.** As regras ficam em `backend/src/main/resources/regras.json`, legíveis e auditáveis por um médico. Uma decisão sobre quem opera primeiro precisa ser reproduzível: a mesma entrada dá sempre a mesma fila.

## Como o `fila_priorizada.csv` está montado (919 de 941)

| Posições | O que são |
|---|---|
| 1 a 895 | **Fila ativa**, na ordem do FilaVIVA |
| 896 a 919 | **24 saídas sugeridas** de pacientes não oncológicos (desistiu, operou em outro serviço ou pelo convênio). Ninguém sai da fila sem decisão humana: elas ficam no fim, com a justificativa começando por "SAÍDA SUGERIDA, aguardando confirmação do regulador" e a classe que teriam se continuassem. O regulador confirma ou recusa na tela "Alertas → Saídas sugeridas"; se recusar, o paciente volta para a posição calculada. |

**Ficaram fora só as 22 solicitações duplicadas** (mesmo paciente e mesmo procedimento). Mesclamos na de entrada mais antiga, porque o paciente espera desde lá; eventos e observações da duplicata passam para a principal. 5 delas são oncológicas (`SOL-2023-00088`, `SOL-2026-00865`, `SOL-2026-00864`, `SOL-2026-01014`, `SOL-2026-00816`) e **a principal de cada uma está no arquivo**, com o mesmo CID. Se uma duplicata tivesse CID de câncer e a principal não, o CID de câncer passaria para a principal.

O arquivo passa no `verificar_fila_priorizada.py`: regra de ouro respeitada, 100% dos oncológicos no primeiro terço, 95% dos pacientes com PS ou internação recente na primeira metade e 94% dos sem urgência no último terço.

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
