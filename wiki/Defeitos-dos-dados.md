# Defeitos dos dados

O critério "Execução técnica" pede que a solução **trate os defeitos dos dados**. O que fizemos:

| Defeito | Tratamento |
|---|---|
| CID sem ponto (32) | Normalizado: maiúsculo e sem ponto para comparar (`K409`); exibido com ponto (`K40.9`) |
| Prioridade com grafia variada (23) ou vazia (11) | Minúscula e sem acento; vazia é "sem prioridade" |
| Município com caixa errada (50) | Convertido para "Porto Alegre" |
| Solicitações duplicadas (22) | Mescladas na mais antiga; eventos e observações passam para a principal |
| Eventos duplicados (7) e órfãos (9) | Guardados no banco, ignorados no cálculo |
| Flags manuais erradas (44 flags oncológicas não batem com o CID) | Ignoradas nas regras; viram aviso de reavaliação |
| Biópsia positiva com CID benigno ou incerto (23) | Tratados como oncológicos (A2) e sinalizados como cadastro errado |
| Informação só no texto livre | Dicionário de alarmes com negação, hipótese e histórico (`regras.json`) |
| Dor, limitação ou ASA vazios | Nunca rebaixam: no mínimo classe C e "dado incompleto" para reavaliação |

Código: `backend/src/main/java/com/hackatona/timevermelho/importacao/`.
