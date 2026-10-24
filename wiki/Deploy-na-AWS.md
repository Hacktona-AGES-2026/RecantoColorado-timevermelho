# Deploy na AWS

**No ar desde 24/10/2026, 16h47:** <http://18.218.2.12> (EC2 `m7i-flex.large`, us-east-2). Testado lá: 9 telas, 919 pacientes, CSV oficial idêntico ao do repositório e verificador oficial OK nas duas filas.

Uma EC2 com Docker roda o backend e o frontend (`docker-compose.prod.yml`). Só o frontend fica exposto, na porta 80, e ele repassa `/api` para o backend pela rede interna do Docker.

| Arquivo | Para quê |
|---|---|
| `backend/Dockerfile` | Imagem do backend (Java 25), com o dataset `data/` dentro |
| `frontend/Dockerfile` | Imagem do frontend (Next.js 16) |
| `docker-compose.prod.yml` | Sobe os dois. A chave da IA vem de `backend/.env` na hora de rodar e nunca entra na imagem |
| `deploy/ec2-user-data.sh` | Ao criar a EC2: instala o Docker, clona o repositório e sobe o FilaVIVA |

Resumo:

1. Crie uma EC2 Amazon Linux 2023: `m7i-flex.large` no plano gratuito (a `t3.medium` aparece bloqueada nele) ou `t3.medium` em conta paga.
2. Escolha **sem par de chaves**: o acesso é pelo **EC2 Instance Connect** do console.
3. Abra duas portas: a 22, só para a lista de prefixos `ec2-instance-connect` da região, e a 80, para todos.
4. Cole `deploy/ec2-user-data.sh` em *User data*.

Em uns 7 a 10 minutos, abra `http://<IP público>/`. A chave da IA entra depois, pelo terminal do Instance Connect, em `/opt/filaviva/backend/.env`.

Passo a passo completo, custos, atualização e HTTPS: [docs/DEPLOY-AWS.md](../blob/main/docs/DEPLOY-AWS.md).

Rodando numa máquina qualquer com Docker:

```bash
docker compose -f docker-compose.prod.yml up -d --build
```
