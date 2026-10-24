# Abordagem e usuário

Responde ao que o documento oficial pede no DECISOES.md: **qual abordagem, para qual usuário, por quê**.
O que ficou de fora está em [O que ficou de fora](O-que-ficou-de-fora).

## A abordagem em uma frase

Um **framework clínico explícito (SWALIS adaptado)** decide a urgência; um **score explicável** ordena dentro da
mesma urgência; a fila é **recalculada a cada evento novo**; e o **regulador tem a última palavra**, sempre com
justificativa registrada.

## Para quem

- **Usuário principal: o regulador da fila cirúrgica.** É quem já decide hoje e quem responde pela decisão. O
  sistema ajuda a decidir melhor; não decide por ele.
- **Usuário secundário: o médico especialista.** Cada médico cadastrado vê a fila completa, os pacientes cirúrgicos
  da sua especialidade e, à parte, quem pode precisar de avaliação dele. Ver
  [Médicos por especialidade](Medicos-por-especialidade).

## Quando

- **A cada evento novo**: uma biópsia, uma ida ao PS ou uma reavaliação recalcula a fila inteira na hora
  (cerca de 0,4 s para os 919 pacientes) e gera um alerta com o impacto, por exemplo "subiu da posição 412 para 18,
  classe C → A2, por causa da biópsia positiva de hoje".
- **Na revisão diária**: a tela de Alertas reúne quem precisa ser revisto.

## Por quê

- **Explicável e auditável**: regras clínicas explícitas, legíveis por um médico, com a evidência citada em cada
  posição. A mesma entrada dá sempre a mesma fila.
- **Seguro**: a regra de ouro é uma restrição, não um peso no score. Ver [Regra de ouro](Regra-de-ouro).
- **Humano no controle**: o sistema propõe; o profissional de saúde decide, com justificativa obrigatória.
- **Sem LLM no caminho da decisão**: não há rótulo de "ordem certa" para treinar, e a banca pede decisões
  explicáveis. Ver [Decisões difíceis](Decisoes-dificeis).
