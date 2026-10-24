# FilaVIVA

Plataforma de priorização cirúrgica explicável para o **regulador da fila cirúrgica do SUS**.
9ª Hackatona de Engenharia de Software · PUCRS · Desafio 1 (Fin-x) · Time Vermelho (Recanto Colorado).

> Como decidir, de forma justa, segura e explicável, quem deve operar primeiro, e manter essa decisão
> atualizada enquanto o paciente espera? — pergunta do desafio.

O FilaVIVA organiza a fila por critérios clínicos (SWALIS adaptado), explica cada posição com a evidência citada,
recalcula a fila a cada evento clínico novo e deixa a decisão final com o profissional de saúde.

## Por onde começar

| Quero… | Página |
|---|---|
| Rodar o sistema numa máquina limpa | [Como rodar](Como-rodar) |
| Colocar no ar numa máquina da AWS | [Deploy na AWS](Deploy-na-AWS) |
| Entender a abordagem, o usuário e o porquê | [Abordagem e usuário](Abordagem-e-usuario) |
| Saber como a ordem é calculada | [Motor de priorização](Motor-de-priorizacao) |
| Ver como a regra de ouro é garantida | [Regra de ouro](Regra-de-ouro) |
| Entender como um rebaixamento é sugerido e confirmado | [Rebaixamentos](Rebaixamentos) |
| Entender a fila da tela × o arquivo de entrega | [Duas filas](Duas-filas) |
| Ver as listas de cada médico | [Médicos por especialidade](Medicos-por-especialidade) |
| Registrar se o paciente quer ser operado | [Desejo do paciente](Desejo-do-paciente) |
| Comunicar benefícios, riscos, alternativas e não fazer nada | [BRAN](BRAN) |
| Saber se o paciente pode ser operado agora e quanto vai consumir | [Aptidão cirúrgica](Aptidao-cirurgica) |
| Conhecer as telas | [Telas](Telas) |
| Integrar com a API | [API](API) |
| Conferir o `fila_priorizada.csv` | [fila_priorizada.csv](Fila-priorizada-csv) |
| Ver como tratamos os defeitos dos dados | [Defeitos dos dados](Defeitos-dos-dados) |
| Ler as decisões difíceis | [Decisões difíceis](Decisoes-dificeis) |
| Saber o que ficou de fora de propósito | [O que ficou de fora](O-que-ficou-de-fora) |
| Ver os testes e a validação | [Testes e validação](Testes-e-validacao) |
| Acompanhar o que mudou a cada commit | [Histórico de alterações](Historico-de-alteracoes) |

## Entregáveis do desafio (seção 7 do documento oficial)

| # | Entregável | Onde está |
|---|---|---|
| 1 | Repositório público: código, README para rodar numa máquina limpa e DECISOES.md | Este repositório · [README](../blob/main/README.md) · [DECISOES.md](../blob/main/DECISOES.md) · [Como rodar](Como-rodar) |
| 2 | Solução funcionando sobre o dataset completo | Backend Spring Boot + frontend Next.js, 941 solicitações importadas, 919 na fila |
| 3 | `fila_priorizada.csv` (`solicitacao_id, posicao, justificativa`) | Raiz do repositório · [fila_priorizada.csv](Fila-priorizada-csv) |
| 4 | Pitch de 5 minutos | Roteiro da demo em [docs/ROTEIRO-DEMO.md](../blob/main/docs/ROTEIRO-DEMO.md) |

## Garantias

- A **regra de ouro** ("nenhum paciente com neoplasia maligna, CID iniciado por C, pode ficar abaixo de um paciente
  sem urgência clínica") é uma restrição explícita, checada a cada recálculo nas duas filas. Se falhar, o sistema para.
- Toda posição tem justificativa com a evidência e a fonte (coluna, evento ou trecho do texto).
- O humano tem a última palavra: o regulador confirma, muda a classe ou decide uma saída, sempre com justificativa
  registrada. Ninguém sai da fila sem decisão humana.
- **Nenhuma classe cai sem o regulador**: rebaixamento é só sugerido, com fatores e evidências, e vale depois da
  confirmação. Paciente com câncer nunca tem a classe reduzida, nem de A1 para A2. Ver
  [Rebaixamentos](Rebaixamentos).

## Stack

Backend Spring Boot 4.1.2 · Java 25 · Maven · PostgreSQL 16 (H2 nos testes).
Frontend Next.js 16 · TypeScript · Tailwind · shadcn/ui.

> Esta wiki é mantida junto com o código: as páginas ficam na pasta `wiki/` do repositório e são atualizadas no
> mesmo commit de cada mudança. Ver [Histórico de alterações](Historico-de-alteracoes).
