# O que ficou de fora de propósito

O documento oficial pede que o DECISOES.md diga **o que ficou de fora de propósito**:

- **Agenda de sala, preparo pré-operatório e cancelamentos**: fora do escopo do desafio, que é priorização. A HbA1c
  acima de 8,5% aparece só como alerta de descompensação, sem mexer na urgência cirúrgica.
- **Machine learning**: não há rótulo de "ordem certa" para treinar, e a banca pede decisões explicáveis.
- **Login e perfis de usuário**: o campo "autor" registra quem decidiu, e o cadastro de médicos não tem senha. Em
  produção viria do login do hospital.
- **Atribuir pacientes ao médico por comorbidade**: o médico só recebe como seus os pacientes com procedimento da
  especialidade; os demais aparecem apenas como "Possível avaliação".
- **Questionário do paciente ("voz do paciente")**: próximo passo natural. Como a fila já recalcula a cada evento,
  uma resposta de questionário seria só mais um tipo de evento.
