# Deploy na AWS

Uma EC2 com Docker roda o backend e o frontend (`docker-compose.prod.yml`). Só o frontend fica exposto, na porta 80, e ele repassa `/api` para o backend pela rede interna do Docker.

| Arquivo | Para quê |
|---|---|
| `backend/Dockerfile` | Imagem do backend (Java 25), com o dataset `data/` dentro |
| `frontend/Dockerfile` | Imagem do frontend (Next.js 16) |
| `docker-compose.prod.yml` | Sobe os dois. A chave da IA vem de `backend/.env` na hora de rodar e nunca entra na imagem |
| `deploy/ec2-user-data.sh` | Ao criar a EC2: instala o Docker, clona o repositório e sobe o FilaVIVA |

Resumo: crie uma EC2 Amazon Linux 2023 `t3.medium` com as portas 22 (só o seu IP) e 80 abertas e cole `deploy/ec2-user-data.sh` em *User data*. Em uns 10 minutos, abra `http://<IP público>/`. A chave da IA entra depois, por SSH, em `/opt/filaviva/backend/.env`.

Passo a passo completo, custos, atualização e HTTPS: [docs/DEPLOY-AWS.md](../blob/main/docs/DEPLOY-AWS.md).

Rodando numa máquina qualquer com Docker:

```bash
docker compose -f docker-compose.prod.yml up -d --build
```
