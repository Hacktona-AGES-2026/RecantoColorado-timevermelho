# FilaVIVA

Plataforma de priorização cirúrgica explicável para o regulador do SUS.
Organiza a fila por critérios clínicos (SWALIS adaptado), explica cada posição, reage a cada evento novo, permite simular mudanças no quadro do paciente e deixa a decisão final com o profissional de saúde.

9ª Hackatona de Engenharia de Software · PUCRS · Desafio 1 (Fin-x).
Por que decidimos assim: [DECISOES.md](DECISOES.md) · plano e divisão do time: [PLANO.md](PLANO.md) e [TASKS.md](TASKS.md).

## O que tem

| Tela | Para quê |
|---|---|
| **Fila** (`/`) | A fila inteira ordenada por classe SWALIS e score, com a mudança de posição em relação à ordem de hoje, filtros e download do `fila_priorizada.csv` |
| **Paciente** (`/paciente/{id}`) | Por que o paciente está nessa posição: regra que decidiu a classe, evidências citadas, composição do score, linha do tempo. O regulador confirma, muda a classe ou decide uma saída, com justificativa obrigatória, e pode registrar um evento clínico novo |
| **Alertas e reavaliação** (`/alertas`) | O que mudou, quem precisa ser revisto (prioridade divergente, esquecidos, flags erradas, dados faltando) e saídas sugeridas |
| **Simulador** (`/simulador`) | "E se?" para um paciente (dor, limitação, evento hipotético) e para os pesos do score, sem salvar nada |
| **Antes × depois** (`/indicadores`) | A ordem de hoje comparada com a do FilaVIVA |

Garantias:
- A regra de ouro é uma restrição explícita, checada a cada recálculo nas duas filas.
- Na **fila operacional** (a da tela), ela vale sobre a situação clínica atual, inclusive eventos novos. Uma promoção manual sem evidência não passa à frente de quem tem câncer.
- Na **fila de referência** (o `fila_priorizada.csv`), ela vale com exatamente a definição do verificador oficial.
- Nenhuma classe cai sem o regulador: um rebaixamento é só sugerido (com fatores e evidências) e vale depois que o regulador confirma; se ele rejeita, a classe vigente fica. Paciente com câncer nunca tem a classe reduzida, nem de A1 para A2.
- As prioridades são propostas, e a decisão é do regulador.

**Demo ao vivo:** na tela de Alertas, "Simular chegada de novos eventos" registra eventos sintéticos em pacientes reais e mostra a fila reagindo. "Reiniciar demo" volta ao dataset original (reiniciar o backend também volta).

## Estrutura

```
backend/             API Spring Boot 4.1.2 · Java 25 · Maven
frontend/            Next.js 16 · TypeScript · Tailwind · shadcn/ui · Recharts
data/                dataset do desafio (CSVs, dicionário, verificador)
docs/                documento do desafio, DECISOES.md, material do pitch
docker-compose.yml   PostgreSQL 16
```

## Pré-requisitos

- JDK 25 ou mais novo (`java -version`)
- Node.js 20+ (`node -v`)
- Python 3 (só para rodar o verificador da fila)
- Docker **não** é necessário: por padrão o backend usa banco H2 em memória

## Como rodar

**1. Backend** (porta 8080; importa o dataset de `data/` ao subir)

```bash
cd backend
./mvnw spring-boot:run
```

No Windows (PowerShell/cmd): `.\mvnw.cmd spring-boot:run`.
Teste rápido: <http://localhost:8080/api/status>.
Para ligar o assistente de IA: `cp backend/.env.example backend/.env` e cole a chave (o backend lê esse arquivo sozinho). Modelo e demais configurações já estão no código; veja [docs/AGENTE-IA.md](docs/AGENTE-IA.md).

**2. Frontend** (porta 3000)

```bash
cd frontend
npm install
npm run dev
```

Abra <http://localhost:3000>. A página inicial mostra se a API está no ar.
O navegador chama `/api/...` no próprio Next, que repassa para `http://localhost:8080` (`next.config.ts`). Por isso funciona também em `127.0.0.1`, pelo IP da rede ou em outra porta. Backend em outro endereço: rode o front com `BACKEND_URL=http://host:porta`.

**Opcional: PostgreSQL** em vez do H2: `docker compose up -d db` e suba o backend com `DB_URL=jdbc:postgresql://localhost:5432/filaviva DB_USER=filaviva DB_PASSWORD=filaviva`.

## Testes

```bash
cd backend
./mvnw test
```

Os testes usam banco H2 em memória: não precisam do Docker.

Com banco, backend e frontend no ar, o teste de integração percorre os fluxos da demo e as duas filas (42 verificações) e termina reiniciando a demo:

```bash
python scripts/teste_integracao.py
```

Antes da demo ao vivo, siga [docs/ROTEIRO-DEMO.md](docs/ROTEIRO-DEMO.md). Para validar a classificação com um mentor clínico, use [docs/VALIDACAO-MENTOR.md](docs/VALIDACAO-MENTOR.md).

## Verificar a fila priorizada

O backend grava `fila_priorizada.csv` na raiz do repositório toda vez que sobe. É a **fila de referência — entrega oficial**: só o dataset, sem eventos nem decisões registrados depois, igual a cada execução. Também dá para baixar em <http://localhost:8080/api/fila/export.csv>.

A tela mostra a **fila operacional**, que inclui o que foi registrado na hora. Para baixar exatamente essa fila: `http://localhost:8080/api/fila/export.csv?snapshot=operacional`. O arquivo vem com o nome `fila_operacional_op-N_<horário>.csv`. Ela não é o entregável e pode reprovar no verificador oficial, porque reconhece urgências que ele não vê. Detalhes em "Duas filas" no [DECISOES.md](DECISOES.md). Para conferir (precisa de `pip install pandas`):

```bash
python data/verificar_fila_priorizada.py fila_priorizada.csv --dataset data
```

Além do verificador, `scripts/conferir_csv.py` confere a cobertura (cada solicitação fora do arquivo é uma duplicata com a principal dentro), as justificativas e a coerência das classes, e roda o verificador oficial se o pandas estiver instalado:

```bash
python scripts/conferir_csv.py
```

## Variáveis de ambiente do backend

Nenhuma é obrigatória.

| Variável | Padrão |
|---|---|
| `DB_URL` | `jdbc:h2:mem:filaviva;MODE=PostgreSQL;DB_CLOSE_DELAY=-1` (H2 em memória) |
| `DB_USER` / `DB_PASSWORD` | `sa` / vazio |
| `DATASET_DIR` | `../data` |
| `CORS_ORIGINS` | `http://localhost:[*],http://127.0.0.1:[*]` |
| `PORT` | `8080` |
| `EXPORT_ARQUIVO` | `../fila_priorizada.csv` (vazio = não grava) |

As regras clínicas do texto livre (palavras de alarme, negações, CIDs urgentes) ficam em `backend/src/main/resources/regras.json` e podem ser editadas sem mexer no código.

## Fluxo do time

Branch por tarefa (`feat/motor-swalis`, `feat/front-fila`…), commits pequenos e frequentes, PR curto para a `main`. Divisão de tarefas na seção 4 do [PLANO.md](PLANO.md).
