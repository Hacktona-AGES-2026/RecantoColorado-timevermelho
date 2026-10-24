# Roteiro da demo ao vivo (≈ 2 min do pitch)

Paciente principal: **`SOL-2026-00351`**. Tireoide, CID D44.0 (comportamento incerto), prioridade manual **"baixa"**, mas com biópsia de 13/09/2026: **"LAUDO AP: NEOPLASIA MALIGNA. ENCAMINHAR ONCOLOGIA."**

## Antes de subir no palco (5 min antes)

```bash
docker compose up -d db
cd backend && ./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
cd frontend && npm run dev
python scripts/teste_integracao.py            # 37 verificações; termina reiniciando a demo
```

- O teste tem que terminar com "Tudo certo. A demo foi reiniciada e está limpa."
- Abra as abas na ordem: **Fila**, **Paciente `SOL-2026-00351`**, **Alertas**, **Antes × depois**.
- Navegador em tela cheia e zoom de 110% a 125% (projetor).
- Plano B: vídeo gravado da demo, mais os prints de cada passo.

## Passo a passo

| # | Tela e ação | O que mostrar | O que falar |
|---|---|---|---|
| 1 | **Fila** | Faixa no topo: 919 pacientes, A1 146 · A2 142 · B 224 · C 205 · D 202. Coluna "Δ hoje" com setas verdes e vermelhas | "Esta é a fila inteira, ordenada pela urgência clínica. A seta mostra quanto cada um andou em relação à fila de hoje." |
| 2 | Buscar `SOL-2026-00351` e abrir | Posição **241**, hoje **866**. Classe **A2** | "Pela fila de hoje, ela é a 866ª, com prioridade baixa. Para nós, é a 241ª." |
| 3 | Bloco **"Por que esta classe"** | Regra: *biópsia positiva, tratada como neoplasia mesmo com CID benigno*; evidência citada: o laudo; aviso **"Cadastro com flag errada"** | "Não é caixa-preta: cada posição tem o motivo e a prova. O cadastro ainda diz benigno; o laudo diz câncer." |
| 4 | **Composição do score** | 6 barras, total 48 | "Dentro da mesma classe, desempata o score, e cada ponto tem explicação." |
| 5 | **Registrar evento clínico**: Tipo *Exame de imagem*, Resultado *Progressão*, descrição "TC: aumento da lesão" → **Registrar evento** | Cartão de impacto: **241 → 57, A2 → A1** | "Chegou um exame agora. A fila recalcula na hora: a paciente sobe para a 57ª e vira A1. A fila é um fluxo, não uma foto." |
| 6 | **Alertas e reavaliação** | "O que mudou" com o evento; "Precisa de reavaliação" (prioridade divergente, esquecidos); **Saídas sugeridas** | "Todo dia o regulador vê o que mudou e quem está sendo esquecido. Quem disse que desistiu **não sai sozinho**: continua na fila até alguém confirmar." |
| 7 | Volte ao paciente → **Decisão do regulador** → *Mudar classe* para **D**, com uma justificativa qualquer | Erro: *"Regra de ouro: paciente oncológico não pode ficar abaixo da classe A2…"* | "O sistema propõe, o regulador decide, com justificativa registrada. Mas ninguém consegue jogar um câncer para o fim da fila." |
| 8 | **Antes × depois** | Oncológicos no 1º terço **53% → 100%**; abaixo de quem não tem urgência **172 → 0**; biópsia positiva com CID benigno **755 → 183** | "É isso que muda para quem espera." |
| 9 | (se sobrar tempo) **Alertas → Reiniciar demo** | Fila volta ao estado do dataset | — |

> Se o **Exame de imagem** não aparecer na lista de tipos, use **Internação**, Resultado *Alta*, Valor **10**. O resultado é parecido: **241 → 80, A2 → A1**.
> Se a lista de opções abrir e fechar rápido demais, clique de novo e espere a animação terminar antes de escolher (vimos isso nos testes).

## Números de reserva (para perguntas da banca)

- 941 solicitações; 22 duplicatas mescladas; 919 na fila.
- 44 flags oncológicas erradas; 23 biópsias positivas com CID benigno.
- 74 pacientes com prioridade "baixa" que são A1 ou A2.
- `fila_priorizada.csv`: verificador oficial **OK**, 100% dos oncológicos no 1º terço, 94% dos casos com PS ou internação recente na 1ª metade, 93% dos sem urgência no último terço.
- Revisando caso a caso, corrigimos dois erros do leitor de texto: "asséptica" virava "sepse" (7 pacientes) e "CATE: TRONCO 80%" não era reconhecido (3 pacientes em D/C).

## Depois da demo

Rode `Reiniciar demo` (tela de Alertas) ou reinicie o backend. Os dois voltam ao dataset original.
