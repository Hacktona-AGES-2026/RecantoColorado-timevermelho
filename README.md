# FilaVIVA

Plataforma de priorização cirúrgica explicável para o regulador do SUS.
Organiza a fila por critérios clínicos (SWALIS adaptado), explica cada posição, reage a cada evento novo, permite simular mudanças no quadro do paciente e deixa a decisão final com o profissional de saúde.

9ª Hackatona de Engenharia de Software · PUCRS · Desafio 1 (Fin-x) — plano completo em [PLANO.md](PLANO.md).

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

## Verificar a fila priorizada

```bash
python data/verificar_fila_priorizada.py fila_priorizada.csv
```

## Variáveis de ambiente do backend

| Variável | Padrão |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/filaviva` |
| `DB_USER` / `DB_PASSWORD` | `filaviva` / `filaviva` |
| `DATASET_DIR` | `../data` |
| `CORS_ORIGINS` | `http://localhost:3000` |
| `PORT` | `8080` |

## Fluxo do time

Branch por tarefa (`feat/motor-swalis`, `feat/front-fila`…), commits pequenos e frequentes, PR curto para a `main`. Divisão de tarefas na seção 4 do [PLANO.md](PLANO.md).
