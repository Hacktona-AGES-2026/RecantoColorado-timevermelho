# Validação da classificação com o mentor clínico

> Levar impresso ou no celular. São 10 minutos de conversa com um mentor do Hospital São Lucas ou da Fin-x.
> Objetivo: confirmar as classes A1/A2, os prazos e as decisões sensíveis, **com casos reais do dataset**.
> Anotem as respostas na última seção e citem a validação no pitch.

## 1. As regras, em uma página

| Classe | Prazo | Regra do FilaVIVA | Pacientes |
|---|---|---|---|
| **A1** | 15 d | Câncer com progressão (imagem com progressão, PS/internação nos últimos 180 dias, alarme no texto, reavaliação com piora) | 53 |
| **A1** | 15 d | CID de urgência: I20.0, K40.0, K40.3, K42.0, K43.0, K80.0, N13.2, I83.2 | 46 |
| **A1** | 15 d | Alarme grave no texto do médico (lista abaixo) | 47 |
| **A2** | 21 d | CID C* (câncer confirmado), piso de todo oncológico | 126 |
| **A2** | 21 d | Biópsia positiva com CID benigno ou incerto no cadastro | 16 |
| **B** | 60 d | Dor ≥ 7, limitação grave, PS/internação recente, piora relatada, lesão incerta (D37–D48, R91), sinais de malignidade ou perda funcional, doença grave descrita | 224 |
| **C** | 180 d | Dor 4–6 ou limitação moderada | 192 |
| **C** | 180 d | Dor ou limitação **não informadas** (não rebaixamos para D sem informação) | 13 |
| **D** | 360 d | O resto | 202 |

Dentro da classe, desempata um score de 0 a 100: diagnóstico 30, piora 25, dor 15, limitação 10, tempo de espera relativo ao prazo 15, ASA/risco 5.

**Alarmes graves que levam a A1** (quantos pacientes): glaucoma facomórfico (10), lesão de tronco de coronária (6), sepse (6), insuficiência respiratória ou dessaturação (5), hérnia encarcerada (5), perda de função (5), colangite (3), icterícia (2), angina instável ou em repouso (1), hidronefrose (1), hemoptise (1), obstrução intestinal (1), pancreatite (1).

## 2. Perguntas, com casos para abrir na tela

Abra cada paciente em `http://localhost:3000/paciente/<id>`.

1. **Os prazos (15/21/60/180/360 dias) fazem sentido para o SUS hoje?** Ou o hospital usa outros?
2. **Câncer confirmado e estável (A2) deve ficar abaixo de urgências não oncológicas (A1)?** Exemplo: colangite há 3 semanas (`SOL-2026-00637`, A1, posição 81) acima de um câncer de colo do útero sem sinal de progressão (`SOL-2025-00095`, A2, posição 145). Hoje ordenamos A1 > A2.
3. **Biópsia positiva com CID benigno vale como câncer?** Caso: `SOL-2026-00351`, D44.0 com laudo "NEOPLASIA MALIGNA".
4. **"Risco de" deve contar como alarme?** Caso: catarata hipermadura com **risco** de glaucoma facomórfico, PIO 28 mmHg (10 pacientes em A1). Ex.: `SOL-2026-00337`.
5. **Apneia grave com retenção de CO2 ou dessaturação a 78% (septoplastia) é A1?** 5 pacientes. Ex.: `SOL-2026-00420`.
6. **Doença coronária**: lesão de tronco ≥ 50% → A1 (6 pacientes, ex. `SOL-2026-00072`, posição 39); triarterial, CCS III/IV ou fração de ejeção < 40% → B (8 pacientes, ex. `SOL-2025-00237`: "DAC triarterial, angina CCS III, FE 36%"). Antes da revisão, este último estava em D (hoje: posição 29 na prioridade manual, 311 no FilaVIVA). Os cortes estão certos?
7. **Saídas sugeridas**: hoje quem "desistiu" ou "operou em outro serviço" **continua na fila**, na posição calculada, até o regulador confirmar. Mas 3 deles estão em A1 (ex.: `SOL-2026-00560`, posição ~93). O regulador prefere assim, ou prefere que fiquem no fim da fila até a confirmação?
8. **Paciente com câncer que diz que desistiu**: manter na fila até confirmação é o certo?
9. **Dor ou limitação não informadas** ficam em C (13 pacientes). Melhor C, B ou "aguardando dados"?
10. **HbA1c > 8,5%** hoje só gera alerta de "descompensação: encaminhar para controle", sem mudar a urgência. Está certo?
11. **Regra de ouro na prática**: 2 pacientes com lesão de tronco (`SOL-2026-00960` e `SOL-2026-00765`) têm dor baixa e nenhum evento, então a definição oficial os trata como "sem urgência" e eles ficam logo abaixo de todos os oncológicos (posições 287 e 288), apesar de A1. É aceitável?

## 3. Onde mudar, se o mentor discordar

| O quê | Onde | Exige |
|---|---|---|
| Palavras de alarme, negações, CIDs urgentes, faixa de CID incerto | `backend/src/main/resources/regras.json` | Só reiniciar o backend |
| Prazos das classes | `backend/.../motor/ClasseSwalis.java` | Recompilar |
| Qual sinal leva a qual classe | `backend/.../motor/ClassificadorSwalis.java` | Recompilar |
| Pesos do score | `backend/.../motor/Pesos.java` (ou teste na tela Simulador → Pesos) | Recompilar |
| Saída sugerida na posição ou no fim | `backend/.../motor/MotorPriorizacao.java` (método `avaliar`) | Recompilar |

Depois de qualquer mudança: `./mvnw test`, `python scripts/teste_integracao.py` e `python scripts/conferir_csv.py`.

## 4. Respostas do mentor

| Pergunta | Resposta | Mudamos? |
|---|---|---|
| 1 | | |
| 2 | | |
| 3 | | |
| 4 | | |
| 5 | | |
| 6 | | |
| 7 | | |
| 8 | | |
| 9 | | |
| 10 | | |
| 11 | | |

Mentor: ____________________ · Horário: ______
