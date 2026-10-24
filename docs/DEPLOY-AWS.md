# Deploy do FilaVIVA na AWS (uma EC2 com Docker)

Uma máquina roda o backend e o frontend em Docker. Só o frontend fica exposto, na porta 80, e ele repassa `/api` para o backend pela rede interna do Docker. O banco é H2 em memória: o dataset é reimportado a cada subida, igual ao modo local da demo, e "Reiniciar demo" volta ao dataset original.

Arquivos usados:

| Arquivo | Para quê |
|---|---|
| `backend/Dockerfile` | Imagem do backend (Java 25). Leva o `data/` junto |
| `frontend/Dockerfile` | Imagem do frontend (Next.js 16) |
| `docker-compose.prod.yml` | Sobe os dois. A chave da IA vem de `backend/.env` na hora de rodar e nunca entra na imagem |
| `deploy/ec2-user-data.sh` | Prepara a máquina sozinha ao ser criada: instala o Docker, clona o repositório e sobe o FilaVIVA |

## Antes de começar

- **Conta na AWS** com um meio de pagamento. Crie em <https://aws.amazon.com/>, ative o MFA do usuário raiz e, de preferência, use um usuário IAM para o dia a dia.
- **Repositório público.** O script clona do GitHub sem senha. Com o repositório privado, copie o código com `scp` (no fim desta página).
- **Custo:** a máquina recomendada (t3.medium, 4 GB de memória) custa cerca de US$ 0,04 a 0,07 por hora, dependendo da região. Ligada o mês inteiro, dá uns US$ 30 a 50, mais o disco (cerca de US$ 2). Confira na [calculadora da AWS](https://calculator.aws/) e **pare ou encerre a instância depois do evento**. A t3.micro do nível gratuito tem 1 GB, pouco para o build e para os dois serviços.

## Passo a passo (console da AWS, uns 15 minutos)

1. **Região:** no canto superior direito, escolha `South America (São Paulo) sa-east-1`, mais perto dos usuários, ou `US East (N. Virginia) us-east-1`, mais barata.
2. **EC2 → Launch instance:**
   - **Name:** `filaviva`
   - **AMI:** Amazon Linux 2023 (64-bit x86)
   - **Instance type:** `t3.medium`
   - **Key pair:** crie uma (`filaviva`, tipo RSA, formato `.pem`) e guarde o arquivo baixado. Ele é a única forma de entrar por SSH. Não o coloque no repositório.
   - **Network settings → Create security group** com duas regras de entrada:
     - SSH (porta 22), origem **My IP**
     - HTTP (porta 80), origem **Anywhere (0.0.0.0/0)**
   - **Storage:** 20 GiB gp3
   - **Advanced details → User data:** cole o conteúdo inteiro de [`deploy/ec2-user-data.sh`](../deploy/ec2-user-data.sh)
3. **Launch instance.** O build roda sozinho na primeira subida e leva uns 8 a 12 minutos.
4. **Abra o FilaVIVA:** copie o **Public IPv4 address** da instância e abra `http://<esse IP>/`. A tela inicial mostra se a API está no ar. Teste também `http://<IP>/api/status`.
5. **(Opcional) Endereço fixo:** sem isso, o IP muda quando a instância é parada e ligada de novo. Para fixar: **EC2 → Elastic IPs → Allocate** e depois **Associate** com a instância.

Se a página não abrir depois de 15 minutos, entre por SSH (abaixo) e veja o log da preparação com `sudo tail -50 /var/log/cloud-init-output.log`, e o dos serviços com `cd /opt/filaviva && docker compose -f docker-compose.prod.yml logs --tail 50`.

## Ligar o assistente de IA (chave)

A chave nunca vai no User data, na imagem nem no Git. Coloque direto na máquina:

```bash
ssh -i filaviva.pem ec2-user@<IP>
cd /opt/filaviva
cp backend/.env.example backend/.env
nano backend/.env          # cole a chave em IA_PROVEDOR_1_CHAVE e salve
chmod 600 backend/.env
docker compose -f docker-compose.prod.yml up -d   # recria o backend com a chave
```

Sem a chave, o assistente aparece desligado e o resto do FilaVIVA funciona normalmente.

## Atualizar para a versão mais nova

```bash
ssh -i filaviva.pem ec2-user@<IP>
cd /opt/filaviva
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

## Comandos úteis na máquina

| Para | Comando (em `/opt/filaviva`) |
|---|---|
| Ver se os serviços estão de pé | `docker compose -f docker-compose.prod.yml ps` |
| Ver os logs | `docker compose -f docker-compose.prod.yml logs -f --tail 100` |
| Reiniciar (volta ao dataset original) | `docker compose -f docker-compose.prod.yml restart backend` |
| Parar tudo | `docker compose -f docker-compose.prod.yml down` |

## Sem o repositório público: copiar o código com `scp`

No seu computador, na raiz do repositório (sem `node_modules`, `target` nem `.env`):

```bash
git archive --format=tar.gz -o filaviva.tar.gz HEAD
scp -i filaviva.pem filaviva.tar.gz ec2-user@<IP>:/tmp/
ssh -i filaviva.pem ec2-user@<IP> 'sudo mkdir -p /opt/filaviva && sudo tar xzf /tmp/filaviva.tar.gz -C /opt/filaviva && sudo chown -R ec2-user /opt/filaviva && cd /opt/filaviva && docker compose -f docker-compose.prod.yml up -d --build'
```

## HTTPS (opcional)

Para a demo, `http://` basta. Para HTTPS é preciso um domínio apontando para o Elastic IP. Duas saídas: um load balancer (ALB) com certificado gratuito do ACM, ou um proxy como o Caddy na própria máquina, que pede o certificado sozinho. Nesse caso, abra também a porta 443 no security group.

## Testado

As imagens foram construídas e testadas com o mesmo `docker-compose.prod.yml` numa máquina local com Docker: 919 pacientes na fila, telas abrindo pelo proxy `/api` e o mesmo `fila_priorizada.csv` do repositório no `/api/fila/export.csv`. O deploy numa conta AWS ainda não foi feito, porque a conta não existia.
