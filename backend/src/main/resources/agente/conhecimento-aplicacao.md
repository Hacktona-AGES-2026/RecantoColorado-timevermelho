## Como usar o FilaVIVA (conhecimento para responder dúvidas de uso)

O FilaVIVA ajuda o regulador do SUS a organizar a fila de cirurgias eletivas pela urgência clínica. Ele **sugere** uma ordem e **explica** cada posição; quem decide é sempre o profissional de saúde. As classes e prazos que aparecem nas telas são critérios em validação com um mentor clínico, não protocolo oficial validado: nunca os apresente como recomendação médica.

### Telas

- **Fila** (página inicial): a fila inteira ordenada por classe e, dentro da classe, por uma pontuação. Mostra para cada paciente a posição, a classe, quanto do prazo da classe já passou (a barra), o principal motivo e a coluna "Δ hoje", que diz quantas posições o paciente andou em relação à ordem usada hoje (seta verde = subiu, vermelha = desceu). Dá para filtrar por classe, especialidade, status e "só oncológicos", buscar por nome, número da solicitação, CID ou procedimento e baixar a fila priorizada em arquivo CSV.
- **Paciente**: abre ao clicar em alguém na fila. Explica por que o paciente está naquela posição: a regra que decidiu a classe, as evidências citadas (trechos do laudo ou do texto do médico), a composição da pontuação em seis partes, a linha do tempo de eventos e os motivos de reavaliação. Aqui o regulador pode **confirmar a classe**, **mudar a classe**, **confirmar ou recusar uma saída da fila** (sempre com justificativa de pelo menos 10 caracteres e com o nome de quem decidiu) e **registrar um evento clínico novo** (biópsia, pronto-socorro, internação, exame de imagem, consulta de reavaliação, contato telefônico, HbA1c, falta).
- **Alertas e reavaliação**: mostra o que mudou recentemente, quem precisa ser revisto (prioridade manual divergente da classe clínica, pacientes esquecidos, cadastro com informação errada, dados faltando, sem contato, descompensação de diabetes) e as **saídas sugeridas**. Tem um botão para simular a chegada de novos eventos de demonstração e outro para reiniciar a demonstração.
- **Simulador**: o "e se?". Duas abas: (1) para um paciente, testar o que acontece se a dor ou a limitação mudarem, se chegar um evento hipotético ou se o médico escrever uma frase nova; (2) para a fila toda, testar outros pesos da pontuação. **Nada do simulador é salvo**: a fila oficial não muda.
- **Antes × depois**: compara a ordem de hoje com a ordem do FilaVIVA, com indicadores e gráficos.
- **Assistente** (este chat) e **Relatórios**: o assistente tira dúvidas; os relatórios resumem a fila para os médicos. Ambos são gerados por inteligência artificial e devem ser revisados por uma pessoa.

### Termos que aparecem nas telas

- **Classe**: de A1 (mais urgente, prazo de 15 dias) a D (menos urgente, prazo de 360 dias), passando por A2 (21 dias), B (60 dias) e C (180 dias). O **prazo** é o tempo máximo sugerido para a classe.
- **Posição**: o lugar do paciente na fila ativa, ordenado por classe, pontuação e tempo de espera.
- **Pontuação (score)**: de 0 a 100, desempata pacientes da mesma classe. Soma seis partes: diagnóstico, sinais de piora, dor, limitação funcional, tempo de espera e risco (ASA e risco da cirurgia).
- **Regra de ouro**: nenhum paciente com câncer fica abaixo de alguém sem urgência clínica, e o regulador não consegue colocar um oncológico abaixo da classe A2. Se o diagnóstico mudou, o caminho é registrar o evento clínico novo (por exemplo, uma biópsia).
- **Saída sugerida**: o sistema encontrou sinal de que o paciente desistiu ou operou em outro serviço. **Ele continua na fila, na posição calculada, até o regulador confirmar a saída.** Se o regulador recusar, o paciente permanece.
- **Dado faltando**: quando dor ou limitação não foram informadas, o paciente **não** é rebaixado; ele vai para a lista de reavaliação.
- **Simulação**: resultado hipotético, nunca grava nada.
- **Consulta de reavaliação**: consulta em que o paciente é reavaliado enquanto espera. O resultado dela ("piora", por exemplo) entra como evento e pode mudar a classe do paciente.

### O que o sistema não faz

Não dá diagnóstico, não prescreve, não decide quem opera e não prevê a evolução de ninguém. É um apoio à decisão de um protótipo de hackatona, com dados sintéticos.
