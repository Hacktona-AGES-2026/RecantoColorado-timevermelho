# Motor de priorização

Código: `backend/src/main/java/com/hackatona/timevermelho/motor/`. O motor é Java puro, sem Spring, testável isolado.

## Etapas

1. **Limpeza dos dados**: ver [Defeitos dos dados](Defeitos-dos-dados).
2. **Sinais**: cada fato clínico vira um sinal **com a evidência citada** (trecho do laudo, frase do médico ou valor
   da coluna). Vêm das colunas (CID, dor, limitação, ASA), dos eventos (biópsia, PS, internação, imagem,
   reavaliação, contato, HbA1c) e do texto livre (justificativa e observações).
3. **Classe SWALIS**: a regra mais alta que bater vence.
4. **Score 0–100** ordena dentro da classe.
5. **Trava da regra de ouro**: ver [Regra de ouro](Regra-de-ouro).
6. **Justificativa** em texto e **motivos de reavaliação** para cada paciente.

## Classes SWALIS

| Classe | Prazo | Regra |
|---|---|---|
| A1 | 15 dias | Câncer com sinal de progressão (imagem, PS/internação recente, alarme no texto, reavaliação com piora); CID de urgência (ex.: hérnia com obstrução, colecistite aguda); ou alarme grave no texto (encarceramento, sepse, colangite, perda de função…) |
| A2 | 21 dias | CID C* **ou biópsia positiva** (piso de todo paciente com câncer) |
| B | 60 dias | Dor ≥ 7, limitação grave, PS/internação recente, reavaliação com piora, relato de piora, lesão incerta (D37–D48, R91), sinais de malignidade ou perda funcional no texto |
| C | 180 dias | Dor 4–6 ou limitação moderada; ou dor/limitação **não informadas** |
| D | 360 dias | O resto |

## Score (0–100)

| Componente | Peso |
|---|---|
| Diagnóstico | 30 |
| Piora | 25 |
| Dor | 15 |
| Tempo de espera relativo ao prazo da classe | 15 |
| Limitação funcional | 10 |
| ASA / risco cirúrgico | 5 |

Cada componente aparece explicado na tela do paciente. O regulador pode testar outros pesos no Simulador, sem
salvar nada.

## Ordem final

Classe → score → % do prazo consumido → data de entrada → id. Depois, a trava da regra de ouro.

## Texto livre: só alerta e evidência

- **Texto novo** (descrição de eventos registrados depois da importação) **nunca muda a classe**. Uma menção
  afirmativa de alarme grave abre **REVISÃO CLÍNICA URGENTE** e dá um pequeno ganho de score dentro da classe.
  Menções negadas ("sem sinais de"), hipotéticas ("risco de", "a descartar") e históricas ("há 5 anos", "prévia")
  são ignoradas.
- **Texto do cadastro**: a classificação é mantida, mas marcada como **classe provisória** e enviada para revisão
  quando é A1/A2. São 85 pacientes; 12 deles cairiam de A1 para D se o texto fosse ignorado.
- Os dicionários ficam em `regras.json`. Ainda é leitura por palavras-chave, **não validada por profissional de
  saúde** (ver `docs/VALIDACAO-MENTOR.md`).

## O regulador

- Pode **confirmar** a classe vigente, **subir** a classe ou **decidir uma saída**, sempre com justificativa e autor
  registrados na auditoria.
- **Baixar** a classe é só uma sugestão até o regulador confirmar; um oncológico nunca tem a classe reduzida, nem de
  A1 para A2. Ver [Rebaixamentos](Rebaixamentos).
- Uma **promoção acima da classe calculada** só passa à frente de pacientes com câncer se houver evidência
  estruturada de agravamento registrada (PS ou internação recente, progressão em imagem, reavaliação com piora ou
  CID de urgência). Sem isso, a decisão é registrada, mas a posição fica logo abaixo do último paciente protegido.
