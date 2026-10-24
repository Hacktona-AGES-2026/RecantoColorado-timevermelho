# Telas

| Tela | Rota | Para quê |
|---|---|---|
| **Fila** | `/` | A fila inteira por classe SWALIS e score, com a coluna **Saúde** (diabetes, última HbA1c e comorbidades do cadastro), com a mudança de posição em relação à ordem de hoje, filtros, aviso de fila operacional e os botões "Exportar CSV oficial — Banca" e "Exportar CSV operacional" |
| **Paciente** | `/paciente/{id}` | Por que o paciente está nessa posição: regra que decidiu a classe, evidências citadas, composição do score e linha do tempo. O regulador confirma, muda a classe ou decide uma saída, com justificativa obrigatória, e pode registrar um evento clínico novo |
| **Médicos** | `/medicos` | Cadastro de médicos (nome, CRM, especialidade) e lista dos cadastrados, com quantos pacientes cada um tem |
| **Médico** | `/medicos/{id}` | Abas: pacientes cirúrgicos da especialidade, possível avaliação da especialidade, fila completa e minhas alterações. Botão **Prioridade** em cada paciente. Ver [Médicos por especialidade](Medicos-por-especialidade) |
| **Alertas e reavaliação** | `/alertas` | O que mudou, quem precisa ser revisto (revisão clínica urgente, divergência de prioridade, esquecidos, flags erradas, dados faltando), **rebaixamentos sugeridos** (confirmar ou rejeitar ali mesmo, com justificativa) e saídas sugeridas |
| **Simulador** | `/simulador` | "E se?" para um paciente (dor, limitação, evento hipotético) e para os pesos do score, sem salvar nada. Quando nada muda, explica o porquê; um evento montado e não adicionado entra na simulação |
| **Antes × depois** | `/indicadores` | A ordem de hoje comparada com a do FilaVIVA |

Na Fila e na lista do médico, um selo discreto marca o rebaixamento: `↓?` pendente e `↓✕` bloqueado (oncológico).
O histórico completo (confirmado ou rejeitado) fica na tela do paciente.

## Demo ao vivo

Na tela de Alertas, "Simular chegada de novos eventos" registra eventos sintéticos em pacientes reais e mostra a
fila reagindo. "Reiniciar demo" volta ao dataset original. Roteiro completo em
[docs/ROTEIRO-DEMO.md](../blob/main/docs/ROTEIRO-DEMO.md) (paciente concreto: `SOL-2026-00351`).
