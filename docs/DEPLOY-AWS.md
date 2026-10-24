# Deploy do FilaVIVA na AWS (uma EC2 com Docker)

> **No ar desde 24/10/2026, 16h47:** <http://18.218.2.12>. EC2 `m7i-flex.large` em `us-east-2` (Ohio), Amazon Linux 2023,
> criada com o `deploy/ec2-user-data.sh` deste repositório. O endereço muda se a instância for parada e ligada de novo
> (veja "Endereço fixo"). Depois do evento, encerre a instância para não gastar créditos.

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
- **Tipo de máquina e custo:** são precisos pelo menos 4 GB de memória para o build e os dois serviços (a t3.micro, com 1 GB, não aguenta).
  - **Conta no plano gratuito** (o caso da nossa): a t3.medium aparece bloqueada. Use a **`m7i-flex.large`** (2 vCPUs, 8 GB), que o plano gratuito libera ("Qualificado para o nível gratuito") e paga com os créditos da conta. A `c7i-flex.large` (4 GB) também serve.
  - **Conta paga:** a `t3.medium` (4 GB) custa cerca de US$ 0,04 por hora em us-east-2, uns US$ 30 por mês ligada, mais o disco (cerca de US$ 2).
  - Confira na [calculadora da AWS](https://calculator.aws/) e **encerre a instância depois do evento**.

## Passo a passo (console da AWS, uns 15 minutos)

1. **Região:** no canto superior direito, escolha `South America (São Paulo) sa-east-1`, mais perto dos usuários, ou `US East (N. Virginia) us-east-1`, mais barata.
2. **EC2 → Launch instance:**
   - **Name:** `filaviva`
   - **AMI:** Amazon Linux 2023 (64-bit x86)
   - **Instance type:** `m7i-flex.large` (plano gratuito) ou `t3.medium` (conta paga)
   - **Key pair:** **Proceed without a key pair**. O acesso à máquina é pelo botão **Connect → EC2 Instance Connect** do próprio console, sem arquivo `.pem` para guardar. Se preferir SSH do seu computador, crie um par (`.pem`) e nunca o coloque no repositório.
   - **Network settings → Create security group** com duas regras de entrada:
     - SSH (porta 22): origem **Custom** → lista de prefixos `com.amazonaws.<região>.ec2-instance-connect` (digite "instance-connect" e escolha a da sua região). Assim só o Instance Connect da AWS alcança a porta 22. Com par `.pem`, use **My IP**.
     - HTTP (porta 80), origem **Anywhere (0.0.0.0/0)**. Não marque HTTPS.
   - **Storage:** 20 GiB gp3
   - **Advanced details → User data:** cole o conteúdo inteiro de [`deploy/ec2-user-data.sh`](../deploy/ec2-user-data.sh)
3. **Launch instance.** O build roda sozinho na primeira subida e leva uns 8 a 12 minutos.
4. **Abra o FilaVIVA:** copie o **Public IPv4 address** da instância e abra `http://<esse IP>/`. A tela inicial mostra se a API está no ar. Teste também `http://<IP>/api/status`.
5. **(Opcional) Endereço fixo:** sem isso, o IP muda quando a instância é parada e ligada de novo. Para fixar: **EC2 → Elastic IPs → Allocate** e depois **Associate** com a instância.

Se a página não abrir depois de 15 minutos, entre na máquina (abaixo) e veja o log da preparação com `sudo tail -50 /var/log/cloud-init-output.log`, e o dos serviços com `cd /opt/filaviva && sudo docker compose -f docker-compose.prod.yml logs --tail 50`. Na nossa subida (`m7i-flex.large`), a página respondeu uns 7 minutos depois de a instância ser criada.

## Entrar na máquina

No console: **EC2 → Instances → filaviva → Connect → aba EC2 Instance Connect → usuário `ec2-user` → Connect**. Abre um terminal no navegador. Com um par `.pem`: `ssh -i filaviva.pem ec2-user@<IP>`.

## Ligar o assistente de IA (chave)

A chave nunca vai no User data, na imagem nem no Git. No terminal da máquina (Instance Connect), rode o comando curto abaixo e cole a chave **uma vez** quando ele pedir. Ela não aparece na tela.

```bash
cd /opt/filaviva && git pull -q && bash deploy/colocar-chave.sh
```

O `deploy/colocar-chave.sh` grava a chave em `backend/.env`, com permissão só do dono, e reinicia o backend. Ele mostra só o tamanho da chave, nunca a chave.

Ele existe porque o terminal do navegador costuma estragar o que se cola nele. Dá para colar a chave pelo `nano`, mas aí entram junto códigos invisíveis e, às vezes, o texto repetido, e a OpenAI recusa a chave ("chave recusada" em `/api/agente/status`). O script limpa isso: tira os códigos e, se a chave vier repetida, usa só a primeira.

Sem a chave, o assistente aparece desligado e o resto do FilaVIVA funciona normalmente.

## Atualizar para a versão mais nova

No terminal da máquina (Instance Connect):

```bash
cd /opt/filaviva
git pull
sudo docker compose -f docker-compose.prod.yml up -d --build
```

## Erro 403 no navegador ("Invalid CORS request")

Se o assistente mostrar "Erro 403", ou se registrar evento, decidir ou reiniciar a demo falhar, o backend está recusando a origem do site. O `docker-compose.prod.yml` já aceita qualquer origem (`CORS_ORIGINS`), porque o backend não fica exposto e a API não usa cookies. Numa máquina criada antes dessa correção, atualize:

```bash
cd /opt/filaviva && git pull -q && sudo docker compose -f docker-compose.prod.yml up -d
```

## Comandos úteis na máquina

| Para | Comando (em `/opt/filaviva`) |
|---|---|
| Ver se os serviços estão de pé | `sudo docker compose -f docker-compose.prod.yml ps` |
| Ver os logs | `sudo docker compose -f docker-compose.prod.yml logs -f --tail 100` |
| Ver o log da preparação da máquina | `sudo tail -50 /var/log/cloud-init-output.log` |
| Reiniciar (volta ao dataset original) | `sudo docker compose -f docker-compose.prod.yml restart backend` |
| Parar tudo | `sudo docker compose -f docker-compose.prod.yml down` |

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

- **Local:** as imagens foram construídas e testadas com o mesmo `docker-compose.prod.yml` numa máquina com Docker.
- **Na AWS** (24/10/2026, <http://18.218.2.12>):
  - as 9 telas respondem (fila, paciente, médicos, lista do médico, alertas, simulador, antes × depois, relatórios, assistente);
  - 919 pacientes na fila;
  - `/api/fila/export.csv` é idêntico ao `fila_priorizada.csv` do repositório;
  - o verificador oficial dá "RESULTADO: OK" no CSV oficial e na fila da tela;
  - o exame de imagem da demo leva `SOL-2026-00351` de 241 para 57;
  - a porta 8080 do backend não fica exposta.
