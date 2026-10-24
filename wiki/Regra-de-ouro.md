# Regra de ouro

> Nenhum paciente com neoplasia maligna (CID iniciado por C) pode ficar abaixo de um paciente sem urgência clínica.
> — regra eliminatória do desafio.

## Como é garantida

- É uma **restrição explícita** depois da ordenação, não pontos no score (`GuardRegraDeOuro.java`).
- **Ajuste mínimo**: quem é "sem urgência" e estava acima do último paciente protegido desce para logo depois dele;
  quem tem urgência não sai do lugar.
- Roda **a cada recálculo, nas duas filas**. Se a checagem falhar, o sistema lança erro em vez de mostrar uma fila
  insegura.
- Os critérios de urgência do verificador oficial (palavras de alarme e CIDs urgentes) foram copiados literalmente.

## Quem é "sem urgência" e quem é protegido

| | Fila operacional (tela) | Fila de referência (`fila_priorizada.csv`) |
|---|---|---|
| Sem urgência | Classe calculada D, com dor e limitação **informadas**, e nenhum critério de urgência do verificador em todos os eventos (inclusive os novos). Dado ausente nunca conta como calmo | Exatamente o `verificar_fila_priorizada.py`: só eventos do dataset |
| Protegido | CID C* **ou biópsia positiva** | CID C* |

Também não passam à frente de protegidos:

- quem foi **promovido pelo regulador (ou médico) sem evidência estruturada** de agravamento;
- na fila da tela, quem tem uma **urgência que os critérios do verificador oficial não reconhecem** (ex.: "tronco 80%", lida só no texto). A classe continua A1, mas o paciente fica logo abaixo do último oncológico até alguém registrar a reavaliação com piora, o atendimento ou o exame. Assim a fila da tela exportada também passa no verificador oficial, conferido na fila inicial e depois de 600 ações aleatórias.

## Listas por especialidade

As listas dos médicos são recortes da fila operacional **na mesma ordem**. Um recorte de uma fila que respeita a
regra de ouro também a respeita; há teste para todas as especialidades e as duas listas.

## Evidência

- O verificador oficial aprova o `fila_priorizada.csv`: "RESULTADO: OK — regra de ouro respeitada e formato válido".
- Teste de estresse: 5.662 filas e mais de 5 milhões de posições checadas, mais 300 ações pela API real, com
  0 falhas.
- Testes automáticos: 300 cenários aleatórios no motor e 40 ações aleatórias sobre o dataset completo.
