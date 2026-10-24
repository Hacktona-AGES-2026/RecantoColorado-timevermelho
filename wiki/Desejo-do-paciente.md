# Desejo do paciente: quer ser operado?

Cada paciente tem um registro de **se quer ser operado**. O dataset não traz essa informação, então todos começam como
**não informado**.

## Como registrar

Na tela do paciente, o cartão **"O paciente quer ser operado?"** tem três respostas:

| Resposta | O que acontece |
|---|---|
| **Quer ser operado** | Fica registrado. Nada muda na fila |
| **Não quer ser operado** | Fica registrado e vira pendência do regulador ("Paciente não quer ser operado") e selo "não quer operar" na fila e na lista do médico |
| **Em dúvida** | Fica registrado e vira pendência ("Paciente em dúvida sobre a cirurgia") e selo "em dúvida" |

Cada registro guarda a resposta, uma observação opcional, **quem registrou** (regulador, enfermagem ou um médico
cadastrado, com nome e CRM) e **quando**. Todas as respostas ficam no histórico; a mais recente é a que vale.

## O que não muda

- **A fila não muda:** a resposta não entra no cálculo. Classe, score e posição continuam os mesmos.
- **Ninguém sai da fila sozinho:** se o paciente não quer operar, o regulador conversa com ele (benefícios, riscos,
  alternativas e não fazer nada) e, se ele mantiver, confirma a saída na página do paciente. É a mesma regra das saídas
  sugeridas.
- O `fila_priorizada.csv` não muda.
- "Reiniciar demo" apaga as respostas registradas, como os eventos e as decisões.

## API

| Método | Rota | O que faz |
|---|---|---|
| POST | `/pacientes/{id}/desejo-cirurgia` | Registra `{resposta (QUER_OPERAR, NAO_QUER_OPERAR, EM_DUVIDA), observacao, autor, medicoId}` |
| GET | `/pacientes/{id}/desejo-cirurgia` | Histórico de respostas, mais recente primeiro |

O detalhe do paciente (`GET /pacientes/{id}`) traz `desejoCirurgia` e `historicoDesejo`, e cada item da fila traz
`desejoCirurgia`.

## Código

- Backend: `dominio/DesejoCirurgia.java`, `dominio/RespostaCirurgia.java`, `servico/DesejoCirurgiaService.java`
- Frontend: `components/fila/desejo-cirurgia.tsx`
- Testes: `DesejoCirurgiaTest` (8)
