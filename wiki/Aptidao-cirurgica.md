# Aptidão cirúrgica e consumo de recursos

Mesmo que o paciente queira operar, a saúde dele pode pedir um tempo antes: diabetes descontrolado, pressão
descontrolada, doença do coração ou do pulmão aumentam o risco, o tempo de cirurgia, os dias de internação e o uso de
UTI. O FilaVIVA dá um **parecer de aptidão** e uma **estimativa de recursos**, e quem decide é o médico ou o regulador.

## O que não muda (de propósito)

- **A posição na fila não muda.** A prioridade é pela necessidade clínica. Tirar prioridade de quem está doente
  penalizaria justamente quem tem menos acesso a cuidado e reforçaria o viés que o sistema quer evitar.
- Quem precisa otimizar **continua no mesmo lugar** e, com a decisão "aguardar otimização", ganha o selo
  **"não agendar: otimizando a saúde"**: o próximo da fila é chamado e ele volta quando estiver liberado.
- Câncer ou A1 que precisa otimizar: o parecer avisa que a otimização deve ser rápida, dentro do prazo da classe, e que a
  equipe pode operar mesmo assim se o benefício for maior que o risco.
- O `fila_priorizada.csv` não muda.

## Parecer

| Parecer | Quando |
|---|---|
| **Apto** | Risco baixo ou moderado e nada descontrolado |
| **Apto com ressalvas (risco alto)** | 10 pontos ou mais: precisa da liberação do anestesista e dos especialistas antes de agendar |
| **Otimizar antes da cirurgia** | Algo que precisa ser controlado antes de uma cirurgia eletiva: última HbA1c acima de 8,5% (do cadastro ou dos exames) ou pressão descontrolada (informada pelo médico) |

**Só chamamos de diabético quem o CSV registra como diabético**: DM2 na coluna `comorbidades`, `flag_diabetico = sim` ou CID secundário E10–E14. 13 pacientes têm HbA1c acima de 8,5% sem nenhum desses registros. Para eles a tela mostra "HbA1c alta, sem diabetes registrado no cadastro" e pede para investigar, sem dar um diagnóstico que não está no dado. A mesma regra vale para a coluna Saúde, o BRAN e os alertas.

Pontos por fator (todos aparecem na tela, com a fonte):

| Fator | Pontos | Pode melhorar? |
|---|---|---|
| Risco do procedimento (1 a 5) | 1 a 5 | não |
| ASA 3 / ASA 4 | 2 / 4 | não |
| Idade 70–79 / 80 ou mais | 1 / 2 | não |
| Diabetes controlado / **descontrolado (HbA1c > 8,5%)** | 1 / **3, controlar antes** | sim |
| HbA1c subindo na espera | 1 | sim |
| Insuficiência cardíaca / cardiopatia isquêmica | 3 / 2 | não |
| Arritmia ou anticoagulante | 1 | não |
| DPOC / asma / doença renal | 2 / 1 / 2 | não |
| Obesidade / obesidade grave (IMC ≥ 40) | 2 / 3 | sim |
| Tabagismo / hipertensão | 1 / 1 | sim |
| Informados pelo médico: **pressão descontrolada** / colesterol alto / não segue o tratamento | **2, controlar antes** / 1 / 2 | sim |

Risco: até 5 pontos baixo, de 6 a 9 moderado, 10 ou mais alto. Para cada fator, o sistema sugere o que fazer antes
(controlar a glicemia, avaliação cardiológica, parar de fumar…). Pré-requisitos: avaliação pré-anestésica e exames
pré-operatórios em dia.

## Recursos estimados

- **Tempo de cirurgia:** duração média do procedimento, +10% com ASA 3 ou mais, +15% com obesidade (+25% grave), +10%
  com 80 anos ou mais.
- **Dias de internação:** base pelo porte (pequeno 1, médio 2, grande 5, especial 7), +1 dia para insuficiência
  cardíaca, DPOC, doença renal, diabetes descontrolado, 80 anos ou mais e ASA 3 ou mais (até +4).
- **UTI:** prevista quando o procedimento pede; provável/possível conforme o procedimento e o risco.
- **Consumo:** baixo, moderado ou alto, juntando dias de internação e UTI.

## Fatores que o sistema não tem

O dataset não traz colesterol, pressão medida, IMC ou adesão ao tratamento. Em **"Informar fatores de saúde"** o médico
marca colesterol alto, pressão descontrolada, tabagismo atual, "não está seguindo o tratamento", o IMC e outros.

## Decisão

Em **"Registrar decisão"**: **liberar para agendar**, **aguardar otimização** (com plano obrigatório e data para
reavaliar) ou **contraindicar no momento** (conversar sobre alternativas no [BRAN](BRAN)). Fica no histórico, com quem
decidiu e quando. Em Alertas, os grupos **"Otimizar a saúde antes da cirurgia"** e **"Risco cirúrgico alto: liberação
antes de agendar"** mostram quem ainda não tem decisão.

No dataset: 804 aptos, 72 aptos com ressalvas e 43 para otimizar antes.

## Limitações

Pontos, cortes e estimativas são de um protótipo, sem validação clínica: precisam ser revistos com a equipe de
anestesia e calibrados com os dados reais do hospital.
