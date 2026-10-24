# Testes e validação

## Automáticos (`cd backend && ./mvnw test`): 661 testes (incluindo os do agente de IA)

| Classe | Testes | O que cobre |
|---|---|---|
| `MotorPriorizacaoTest` | 30 | Regra de ouro nas duas filas, promoção com e sem evidência, texto afirmativo/negado/hipotético/histórico, classe provisória, dado ausente, biópsia nova, 300 cenários aleatórios |
| `RebaixamentoTest` | 17 | Rebaixamento manual e automático vira sugestão; confirmar; rejeitar mantém a classe; queda de posição não é rebaixamento; oncológico A1→A2 e A2→B/C/D bloqueados por decisão e por recálculo; urgência não oncológica ainda passa; divergência sem rebaixamento falso; CSV oficial com 919 e intacto; consulta na subida não fixa fila vazia |
| `SimulacaoTest` | 5 | Evento de piora muda classe e posição sem gravar nada; o simulador explica quando nada muda (evento neutro, mesmo valor de hoje, sinal que o paciente já tem) e avisa que uma queda de classe seria só sugerida |
| `SnapshotsTest` | 13 | Fila operacional × referência sobre o dataset inteiro, 12 pacientes com urgência só no texto, regressão do `SOL-2026-00351`, 40 ações aleatórias, referência igual ao `fila_priorizada.csv` |
| `MedicoEventoTest` | 7 | Evento sem data fica com a data de hoje e data futura é recusada; exame registrado pelo médico entra no histórico do paciente e em Minhas alterações com nome e CRM; regulador fica registrado com o nome informado; a entrada de auditoria não muda a fila; médico não registra em paciente fora das listas; não dá para criar o registro pela API de ajustes; CSV oficial intacto |
| `SaudePacienteTest` | 4 | Diabetes, HbA1c e comorbidades da coluna Saúde batem com o cadastro e os exames nos 919 pacientes; quem não tem diabetes no CSV nunca é chamado de diabético (fila, aptidão, BRAN, alertas); divergência da flag sinalizada; HbA1c nova aparece e os outros pacientes mantêm a ordem |
| `AptidaoTest` | 8 | Diabetes descontrolado pede otimizar sem mudar a fila; nova HbA1c muda o parecer; fatores do médico entram no parecer e nos recursos; risco alto e UTI da cirurgia cardíaca; decisão humana registrada sem mudar a fila; pendências batem; oncológico com prazo curto; reinício apaga; CSV intacto |
| `BranTest` | 5 | Todo procedimento do dataset tem rascunho com os quatro itens; fatores de risco do paciente (diabetes descontrolado, UTI, câncer); histórico e assinatura do médico; a fila não muda; pendência do desejo mostra se o BRAN foi comunicado; validações; reinício apaga; CSV oficial intacto |
| `DesejoCirurgiaTest` | 8 | Resposta registrada com quem e quando, histórico guardado, "não quer" não muda a fila nem tira dela, pendências do regulador, saída continua com o regulador, médico assina com CRM, reinício apaga, CSV oficial intacto |
| `MedicoPosicaoTest` | 8 | Médico leva paciente com urgência registrada do fim para o 1º lugar sem mudar a ordem dos outros; sem urgência registrada ele para logo depois do último oncológico; oncológico vai para a posição escolhida; só para cima e dentro da fila; liberar volta à posição calculada; posição calculada melhor prevalece; regulador também escolhe; várias posições escolhidas com regra de ouro e CSV oficial intactos |
| `MedicoAjusteTest` | 9 | Médico sobe a classe e fica na auditoria com nome e CRM; a ordem dos outros pacientes não muda; promoção sem evidência não passa câncer; descer vira sugestão; oncológico nunca reduzido; só pacientes das listas dele; saídas e confirmações continuam com o regulador; auditoria fica se o médico for removido; CSV oficial intacto |
| `MedicoServiceTest` | 10 | Lista cirúrgica = exatamente os procedimentos da especialidade, "Possível avaliação" separada, itens idênticos aos da fila geral, ordem e regra de ouro em todas as listas, cadastro e validações |
| `EspecialidadeTest` | 6 | Faixas de CID, comorbidades, oncologia, pediatria, só o procedimento atribui o paciente |
| `DatasetCompletoTest` | 4 | Importação e fila completa |
| `TimevermelhoApplicationTests` | 1 | Contexto Spring |

## Contra o sistema rodando

- `scripts/teste_integracao.py`: 42 verificações dos fluxos da demo e das duas filas; reinicia a demo no fim.
- `scripts/conferir_csv.py` e o verificador oficial sobre o `fila_priorizada.csv`.
- Teste de estresse da regra de ouro: 5.662 filas, mais de 5 milhões de posições e 300 ações pela API, com 0 falhas.

## Pendente

- Validar a classificação com um mentor clínico: `docs/VALIDACAO-MENTOR.md` (regras, contagens, casos e perguntas).
- Validar os critérios de `especialidades.json` com profissionais de cada especialidade.
