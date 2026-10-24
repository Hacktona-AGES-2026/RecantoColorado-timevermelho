# fila_priorizada.csv

Entregável 3 do desafio: `solicitacao_id, posicao, justificativa`, a ordem que a solução produz.

## Como é gerado

- É a **fila de referência** (ver [Duas filas](Duas-filas)): só o dataset, sem eventos nem decisões registrados
  depois. Igual, byte a byte, a cada execução.
- O backend grava o arquivo na raiz do repositório toda vez que sobe. Também sai em
  `GET /api/fila/export.csv` e no botão "Exportar CSV oficial — Banca".

## Conteúdo (919 de 941)

- **Todos os 919 pacientes da fila**, na ordem do FilaVIVA. Distribuição: A1 146, A2 142, B 224, C 205, D 202.
- As **24 saídas sugeridas** (desistiu, operou em outro serviço ou pelo convênio) ficam na posição calculada, com a
  justificativa terminando em "saída sugerida aguardando o regulador (motivo)".
- **Ficaram fora só as 22 solicitações duplicadas** (mesmo paciente e mesmo procedimento), mescladas na de entrada
  mais antiga. As 5 duplicatas oncológicas têm a principal no arquivo, com o mesmo CID.

## Conferência

```bash
python data/verificar_fila_priorizada.py fila_priorizada.csv --dataset data
python scripts/conferir_csv.py
```

Resultado atual do verificador oficial: **"RESULTADO: OK — regra de ouro respeitada e formato válido"**.

- 100% dos oncológicos no primeiro terço.
- 94% dos pacientes com PS ou internação recente na primeira metade.
- 93% dos sem urgência no último terço.

O `conferir_csv.py` confere também a cobertura (cada solicitação fora do arquivo é uma duplicata com a principal
dentro), as justificativas e a coerência das classes.
