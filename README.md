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

Garantias: a regra de ouro é checada a cada recálculo com a mesma definição do verificador oficial, e o regulador não consegue rebaixar um paciente com câncer abaixo de A2.

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
- Docker Desktop aberto (para o Postgres)
- Python 3 (só para rodar o verificador da fila)

## Como rodar

**1. Banco**

```bash
docker compose up -d db
```

**2. Backend** (porta 8080; importa o dataset de `data/` ao subir)

```bash
cd backend
./mvnw spring-boot:run
```

No Windows (PowerShell/cmd): `.\mvnw.cmd spring-boot:run`.
Teste rápido: <http://localhost:8080/api/status>.

**3. Frontend** (porta 3000)

```bash
cd frontend
npm install
npm run dev
```

Abra <http://localhost:3000>. A página inicial mostra se a API está no ar.
Para apontar para outra API, copie `frontend/.env.example` para `frontend/.env.local` e ajuste `NEXT_PUBLIC_API_URL`.

## Testes

```bash
cd backend
./mvnw test
```

Os testes usam banco H2 em memória: não precisam do Docker.

Com banco, backend e frontend no ar, o teste de integração percorre os fluxos da demo (37 verificações) e termina reiniciando a demo:

```bash
python scripts/teste_integracao.py
```

Antes da demo ao vivo, siga [docs/ROTEIRO-DEMO.md](docs/ROTEIRO-DEMO.md). Para validar a classificação com um mentor clínico, use [docs/VALIDACAO-MENTOR.md](docs/VALIDACAO-MENTOR.md).

## Verificar a fila priorizada

O backend grava `fila_priorizada.csv` na raiz do repositório toda vez que sobe. Também dá para baixar em <http://localhost:8080/api/fila/export.csv>. Para conferir (precisa de `pip install pandas`):

```bash
python data/verificar_fila_priorizada.py fila_priorizada.csv --dataset data
```

Além do verificador, `scripts/conferir_csv.py` confere a cobertura (cada solicitação fora do arquivo é uma duplicata com a principal dentro), as justificativas e a coerência das classes, e roda o verificador oficial se o pandas estiver instalado:

```bash
python scripts/conferir_csv.py
```

## Variáveis de ambiente do backend

| Variável | Padrão |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/filaviva` |
| `DB_USER` / `DB_PASSWORD` | `filaviva` / `filaviva` |
| `DATASET_DIR` | `../data` |
| `CORS_ORIGINS` | `http://localhost:3000` |
| `PORT` | `8080` |
| `EXPORT_ARQUIVO` | `../fila_priorizada.csv` (vazio = não grava) |

As regras clínicas do texto livre (palavras de alarme, negações, CIDs urgentes) ficam em `backend/src/main/resources/regras.json` e podem ser editadas sem mexer no código.

## Fluxo do time

Branch por tarefa (`feat/motor-swalis`, `feat/front-fila`…), commits pequenos e frequentes, PR curto para a `main`. Divisão de tarefas na seção 4 do [PLANO.md](PLANO.md).
