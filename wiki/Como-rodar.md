# Como rodar

Passo a passo para uma máquina limpa. Detalhes também no [README](../blob/main/README.md).

## Pré-requisitos

- JDK 25 ou mais novo (`java -version`)
- Node.js 20+ (`node -v`)
- Docker Desktop aberto (para o PostgreSQL)
- Python 3 com `pandas` (só para o verificador da fila)

## 1. Banco

```bash
docker compose up -d db
```

## 2. Backend (porta 8080)

Ao subir, importa o dataset de `data/`, calcula as duas filas, cria os médicos de exemplo e grava o
`fila_priorizada.csv` na raiz do repositório.

```bash
cd backend
./mvnw spring-boot:run
```

No Windows: `.\mvnw.cmd spring-boot:run`. Teste rápido: <http://localhost:8080/api/status>.

O banco é recriado a cada subida (`ddl-auto=create`): eventos, decisões e médicos cadastrados durante a demo voltam
ao estado do dataset quando o backend reinicia.

## 3. Frontend (porta 3000)

```bash
cd frontend
npm install
npm run dev
```

Abra <http://localhost:3000>. Para apontar para outra API, copie `frontend/.env.example` para
`frontend/.env.local` e ajuste `NEXT_PUBLIC_API_URL`.

## Testes

```bash
cd backend
./mvnw test                         # 64 testes, H2 em memória, sem Docker
```

Com banco, backend e frontend no ar:

```bash
python scripts/teste_integracao.py  # 42 verificações dos fluxos da demo; reinicia a demo no fim
python data/verificar_fila_priorizada.py fila_priorizada.csv --dataset data
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

## Arquivos de regras (editáveis sem mexer no código)

- `backend/src/main/resources/regras.json`: palavras de alarme, negações e CIDs urgentes do texto livre.
- `backend/src/main/resources/especialidades.json`: quais pacientes cada especialidade médica atende.
