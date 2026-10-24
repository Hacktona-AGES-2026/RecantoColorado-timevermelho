# Rebaixamentos

> "A solução pode rebaixar alguém? Pode sugerir, com justificativa, para o regulador confirmar. Nunca um oncológico."
> — FAQ do documento oficial do desafio.

## Três situações diferentes

| Situação | O que é | O que o FilaVIVA faz |
|---|---|---|
| **Queda de posição** | Alguém mais grave passou à frente; a classe do paciente não mudou | Nada a confirmar |
| **Rebaixamento de classe** | A classe proposta é menos urgente que a vigente | Só sugere; a classe vigente vale até o regulador decidir |
| **Saída da fila** | Desistência ou cirurgia em outro serviço | Só sugere; sai quando o regulador confirma |

## Como um rebaixamento acontece

1. **Sugestão.** Vem de uma pessoa (pedir em "Mudar classe" uma classe menos urgente) ou do **recálculo automático**
   (quando um recálculo baixaria a classe, por exemplo depois de corrigir um evento). A sugestão registra a classe
   vigente, a proposta, os **fatores** (por que rebaixar) e as **evidências** (regra e sinais da classe calculada).
2. **Classe vigente mantida.** Até a decisão, o paciente continua com a classe e a posição de antes. Ela pode subir
   se chegar evidência nova; nunca cai.
3. **Decisão do regulador**, em Alertas › "Rebaixamentos sugeridos" ou na tela do paciente, com justificativa:
   - **Confirmar**: a classe passa a ser a proposta.
   - **Rejeitar**: a classe vigente fica como piso; a sugestão continua no histórico.
4. Tudo fica na trilha de auditoria (`AjusteRegulador`): quem, quando e por quê.

## Oncológicos

- Paciente oncológico (CID C ou biópsia positiva) **nunca** tem a classe reduzida: nem de A1 para A2, nem de A2 para
  B/C/D.
- Pedido manual: recusado com a explicação (HTTP 422). Nada é registrado como rebaixamento.
- Recálculo que baixaria a classe: vira **rebaixamento bloqueado** (`↓✕`), a classe é mantida e não há como confirmar.
- Se o paciente vira oncológico com uma sugestão pendente, confirmar é recusado; o regulador rejeita para encerrar.
- "Confirmar classe" mantém a classe vigente. Antes voltava para a calculada e podia levar um oncológico promovido de
  A1 para A2.
- Mudanças de posição continuam possíveis: um não oncológico com urgência estruturada pode ficar à frente de um
  oncológico, desde que a [regra de ouro](Regra-de-ouro) seja respeitada.

## Prioridade manual do cadastro

A coluna `prioridade_regulacao` (alta/média/baixa) é, pelo dicionário de dados, "atribuída na inclusão, ruidosa,
raramente revisada". Ela não é uma prioridade clínica reconhecida, e não há equivalência oficial com A1–D. Por isso
a diferença **não** vira rebaixamento pendente; vira o alerta **"Divergência de prioridade — revisão necessária"**:

| Caso | Pacientes | Sinalizado? |
|---|---|---|
| Classe urgente (A1/A2) sem prioridade "alta" | 158 | Sim |
| Prioridade "alta" em C/D (sem dor ou disfunção importantes) | 95 | Sim |
| Prioridade "alta" em B (dor, disfunção ou incapacidade importantes) | 70 | Não: compatível |

Hoje: **253** pacientes com divergência e **0** rebaixamentos pendentes (nenhuma classe foi reduzida).

## O que não muda

- O motor (critérios, pesos, fórmulas) e a ordem geral. A única adição no motor é aplicar o piso da classe vigente.
- O `fila_priorizada.csv`: é a fila de referência, só com o dataset e sem decisões. Continua idêntico e aprovado.
- Reiniciar a demo volta ao dataset de propósito; não gera sugestões.

## Limitações

- Não há login: "regulador" é o nome informado no campo autor. O sistema não impede que a mesma pessoa sugira e
  confirme.
- Uma classe definida por "Mudar classe" fica fixa (comportamento anterior); para voltar a seguir a calculada, o
  rebaixamento precisa ser sugerido para a classe calculada.
