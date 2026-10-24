# Decisões difíceis

Resumo. A versão completa está no [DECISOES.md](../blob/main/DECISOES.md).

- **O tempo de espera nunca passa por cima da urgência clínica.** Um paciente D esperando 4 anos não fica à frente
  de um câncer esperando 3 meses. O tempo pesa dentro da classe e alimenta a lista de **esquecidos** para revisão
  humana.
- **Biópsia positiva vale mais que o CID do cadastro.** Exemplo: `SOL-2026-00351` (CID D44.0, prioridade "baixa",
  laudo "NEOPLASIA MALIGNA") sai da posição 866 para a 241.
- **Usamos o CID, não a flag.** A flag oncológica manual não entra em nenhuma regra; só gera aviso.
- **Dado faltando não rebaixa.** Ninguém vai para o fim da fila por falta de informação.
- **Rebaixamento só sugerido, oncológico nunca rebaixado.** Seguindo a FAQ do desafio, nenhuma classe cai sem o
  regulador confirmar, e um câncer não perde classe nem de A1 para A2. Ver [Rebaixamentos](Rebaixamentos).
- **Prioridade manual não é prioridade clínica.** O cadastro é "ruidoso e raramente revisado"; a diferença vira
  alerta de divergência para revisão, não rebaixamento pendente.
- **Separar a fila da tela do arquivo de entrega.** O verificador só lê o dataset original; a tela precisa
  reconhecer agravamentos novos. Ver [Duas filas](Duas-filas).
- **Promoção manual só passa à frente de câncer com evidência estruturada.** Texto, dor, dado faltando ou relato por
  telefone não bastam sozinhos.
- **Texto livre novo só gera alerta, nunca muda a classe.** Texto do cadastro mantém a classe, marcada como
  provisória e enviada para revisão.
- **Ninguém sai da fila sem decisão humana.** Desistência ou cirurgia em outro serviço viram **saída sugerida**; o
  paciente fica na posição até o regulador confirmar.
- **Lista do médico não reordena nem atribui por comorbidade.** Só o procedimento da especialidade atribui o paciente
  ao médico; CID secundário e comorbidades vão para "Possível avaliação da especialidade", à parte.
- **Sem LLM no caminho da decisão.** A mesma entrada dá sempre a mesma fila, com regras auditáveis por um médico.
