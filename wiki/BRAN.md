# BRAN: comunicar ao paciente

Antes da decisão sobre a cirurgia, o médico comunica ao paciente os quatro itens do **BRAN**:

| Letra | Item | O que dizer |
|---|---|---|
| **B** | Benefícios | O que a cirurgia deve melhorar para ele |
| **R** | Riscos | Os riscos do procedimento e os do próprio paciente |
| **A** | Alternativas | Opções sem cirurgia ou com outros procedimentos |
| **N** | Nada | O que acontece se ele não operar agora |

## Como funciona

1. Na tela do paciente, o cartão **"Conversa BRAN"** tem o botão **"Registrar conversa BRAN"**.
2. O sistema monta um **rascunho** dos quatro itens:
   - texto geral do procedimento (catálogo dos 23 procedimentos do dataset, em `bran-procedimentos.json`);
   - risco do procedimento (1 a 5), porte, duração e se costuma precisar de UTI (`procedimentos.csv`);
   - **fatores de risco do próprio paciente**: ASA 3 ou mais, idade de 75 anos ou mais, diabetes (e diabetes
     descontrolado com HbA1c acima de 8,5%), doença do coração, anticoagulante ou arritmia, DPOC, obesidade, tabagismo e
     doença dos rins;
   - no "Nada", a classe, o prazo e o tempo de espera na fila; para câncer, que a cirurgia tem prioridade.
3. O médico **revisa e edita** os quatro textos (o rascunho avisa que não é recomendação clínica individual),
   escolhe **como foi a conversa** (presencial, telefone ou teleconsulta), anota as **dúvidas** do paciente e confirma
   que ele **recebeu as informações e teve as dúvidas respondidas**.
4. Logo depois, registra a **decisão do paciente** (quer ser operado, não quer, em dúvida), que vai para o
   [Desejo do paciente](Desejo-do-paciente).
5. **"Versão para o paciente"** baixa um texto simples com os quatro itens, as dúvidas e o aviso para procurar o
   serviço de saúde ou o SAMU (192) em emergência.

Cada conversa fica no histórico (quem comunicou, quando, como), e a mais recente é a que vale. Na revisão do regulador,
quem "não quer" ou está "em dúvida" mostra se o BRAN já foi comunicado.

## O que não muda

- A fila: o BRAN não entra no cálculo de classe, score ou posição.
- O `fila_priorizada.csv`.
- "Reiniciar demo" apaga as conversas registradas.

## API

| Método | Rota | O que faz |
|---|---|---|
| GET | `/pacientes/{id}/bran/rascunho` | Rascunho com os quatro itens, os fatores de risco do paciente e o aviso |
| POST | `/pacientes/{id}/bran` | Registra `{beneficios, riscos, alternativas, nada, forma, duvidas, compreendeu, autor, medicoId}` (cada item com 10 caracteres ou mais) |
| GET | `/pacientes/{id}/bran` | Histórico, mais recente primeiro |

## Limitação

O texto do catálogo é informação geral, escrita para o protótipo. Não foi validado por profissional de saúde e precisa ser, antes de
uso real.
