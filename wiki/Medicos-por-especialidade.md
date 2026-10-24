# Médicos por especialidade

Cadastro de médicos com especialidade. Cada médico vê três listas, todas do mais grave para o menos grave:

| Aba | Quem aparece |
|---|---|
| **Pacientes cirúrgicos** | Pacientes cujo **procedimento cirúrgico é da especialidade** do médico. São os atribuídos a ele |
| **Possível avaliação da especialidade** | Pacientes com procedimento de **outra** especialidade, mas com CID principal, CID secundário, comorbidade, câncer ou idade relacionados. **Não** são atribuídos ao médico: só sinalizados, porque podem precisar de parecer antes da cirurgia |
| **Fila completa** | A fila geral inteira |

## O médico muda a prioridade

Em cada linha das listas há o botão **Prioridade**. O médico escolhe a classe, escreve a justificativa (obrigatória)
e pode marcar **"sei de algo que ainda não está no sistema"** (contato direto, exame externo...).

- Usa o **mesmo caminho das decisões do regulador**: a fila é recalculada pelo motor, sem atalho na ordenação. Só o
  paciente alterado muda de lugar; a ordem relativa de todos os outros fica igual (há teste para isso).
- **Subir** a classe vale na hora. Sem atendimento ou exame estruturado registrado, o paciente não passa à frente de
  quem tem câncer ([regra de ouro](Regra-de-ouro)).
- **Descer** a classe vira rebaixamento sugerido, que o regulador confirma ou rejeita ([Rebaixamentos](Rebaixamentos)).
- **Oncológico** nunca tem a classe reduzida, nem de A1 para A2.
- O médico só mexe em pacientes das listas dele (cirúrgicos ou possível avaliação). Confirmar rebaixamento e decidir
  saída da fila continuam com o regulador.
- **Auditoria:** cada alteração guarda nome, CRM, especialidade e id do médico, além do horário e da justificativa.
  A aba **Minhas alterações** mostra o histórico do médico, e o histórico do paciente marca "médico" e
  "informação fora do sistema". O registro fica mesmo se o médico for removido.
- **Exames e eventos:** aberto pela lista do médico, o paciente mostra "Registrando como Dr(a). X". O exame ou evento
  registrado ali entra no histórico do paciente e em Minhas alterações, com o nome, o CRM, o que foi registrado e o
  impacto na fila (só para pacientes das listas dele). O regulador também fica registrado, com o nome que informar.
  Esse registro é só auditoria: quem muda a fila é o próprio evento, recalculado pelo motor.
- O `fila_priorizada.csv` não muda: é a fila de referência, sem decisões.

### Mudar a posição

Na mesma janela, o modo **Mudar posição** deixa o médico escolher a posição do paciente na fila geral, por exemplo
levar o último da lista para o 1º lugar (atalhos "1º da fila" e "1º desta lista").

- **Só para cima.** Baixar um paciente é rebaixar a prioridade: segue o fluxo de rebaixamento sugerido.
- **Os outros só abrem espaço**, na mesma ordem entre si.
- **Regra de ouro:** paciente com câncer, ou com atendimento/exame de piora registrado (PS, internação, progressão em
  imagem, reavaliação com piora, CID de urgência), vai exatamente para a posição escolhida. Sem isso, ele sobe até logo
  depois do último paciente oncológico, e a mensagem explica o motivo.
- Se a posição calculada ficar melhor que a escolhida (por exemplo, depois de uma progressão), vale a calculada.
- A posição escolhida fica até ser **liberada** ("Liberar e voltar para a posição calculada").
- Na Fila e na lista do médico, a posição aparece com o selo **escolhida**; o histórico guarda quem escolheu, a posição,
  a justificativa e se havia informação fora do sistema. O regulador também pode escolher posições (`FIXAR_POSICAO`).

## O que não muda

- As listas **não recalculam nada**: cada paciente é exatamente o item da fila geral, com a mesma posição, classe,
  score e justificativa. A coluna "# geral" mostra a posição na fila completa.
- A ordem é a da fila geral (classe → score → prazo), então a [regra de ouro](Regra-de-ouro) continua valendo
  dentro de cada lista.
- Um evento novo reordena a fila geral e, junto, as listas dos médicos.
- O motor de priorização não foi alterado por esta funcionalidade.

## Critérios (`backend/src/main/resources/especialidades.json`)

O paciente entra se bater em **qualquer** critério; `idadeMaxima`, quando existe, é obrigatória.

| Campo | Exemplo (Cardiologia) |
|---|---|
| `especialidadesCirurgicas` | `Cirurgia Cardiaca`: define a lista **cirúrgica** |
| `cidFaixas` (CID principal) | `I05-I09`, `I20-I52` |
| `cidSecundarioFaixas` | `I05-I09`, `I20-I52` |
| `termosComorbidade` | `icc`, `insuficiencia cardiaca`, `fibrilacao`, `arritmia`, `cardiopatia`, `coronari`… |
| `oncologico` | (Oncologia) CID C ou biópsia positiva |
| `idadeMaxima` | (Pediatria) 17 |

Faixas comparam letra + 2 dígitos do CID (`I20-I52` inclui `I25.1`). Termos de comorbidade casam no começo de
palavra, sem acento. Hipertensão sozinha **não** coloca o paciente na Cardiologia.

## Contagem atual (fila operacional do dataset)

| Especialidade | Cirúrgicos | Possível avaliação |
|---|---|---|
| Cardiologia | 23 | 111 |
| Cirurgia Vascular / Angiologia | 51 | 0 |
| Oncologia | 127 | 111 |
| Ortopedia | 111 | 0 |
| Urologia | 108 | 0 |
| Nefrologia | 0 | 71 |
| Oftalmologia | 99 | 0 |
| Ginecologia | 47 | 0 |
| Mastologia | 52 | 0 |
| Cirurgia Geral / Aparelho Digestivo | 186 | 57 |
| Coloproctologia | 26 | 43 |
| Cirurgia de Cabeça e Pescoço | 36 | 0 |
| Endocrinologia | 0 | 349 |
| Cirurgia Bariátrica | 24 | 0 |
| Pneumologia / Cirurgia Torácica | 15 | 106 |
| Otorrinolaringologia | 14 | 0 |
| Pediatria | 0 | 0 |

- **Pediatria fica vazia**: todos os pacientes do dataset têm 18 anos ou mais.
- **Nefrologia e Endocrinologia** não têm procedimento cirúrgico próprio no dataset, então só têm "Possível
  avaliação". Endocrinologia é grande porque inclui os diabéticos.
- Um paciente com câncer operado por outra especialidade (ex.: mama pela Mastologia) aparece para o oncologista em
  "Possível avaliação", não como paciente cirúrgico dele.

## Médicos de exemplo

Criados na subida (fictícios): Dra. Helena Prado (Cardiologia), Dr. Marcos Teixeira (Oncologia), Dra. Juliana
Ribas (Ortopedia), Dr. Rafael Moura (Pediatria).

## Regras do cadastro

- Nome, CRM e especialidade obrigatórios (HTTP 400 com a mensagem do campo).
- CRM único, sem diferenciar maiúsculas (HTTP 409).
- Especialidade precisa existir no catálogo (HTTP 422).

## Código

- Backend: `backend/src/main/java/com/hackatona/timevermelho/medico/`
- Frontend: `frontend/src/components/medicos/` e as rotas `/medicos` e `/medicos/{id}`
- Testes: `EspecialidadeTest` e `MedicoServiceTest`
