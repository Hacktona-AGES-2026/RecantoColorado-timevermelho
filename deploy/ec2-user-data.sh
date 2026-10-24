#!/bin/bash
# Prepara uma EC2 (Amazon Linux 2023) e sobe o FilaVIVA na porta 80.
# Cole este arquivo em "Advanced details > User data" ao criar a instância (passo a passo em docs/DEPLOY-AWS.md).
# NÃO coloque a chave da IA aqui: o User data fica visível no console da AWS. A chave vai depois, por SSH.
set -euxo pipefail

REPO="https://github.com/Hacktona-AGES-2026/RecantoColorado-timevermelho.git"
DESTINO=/opt/filaviva

dnf install -y docker git
systemctl enable --now docker
usermod -aG docker ec2-user

# Docker Compose v2 (plugin)
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-$(uname -m)" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 2 GB de swap: o build do frontend e do backend passa de 2 GB de memória em máquinas pequenas
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
fi

# o repositório precisa estar público (ou copie o código com scp e pule o clone)
if [ ! -d "$DESTINO/.git" ]; then
  git clone "$REPO" "$DESTINO"
fi
chown -R ec2-user:ec2-user "$DESTINO"
cd "$DESTINO"
docker compose -f docker-compose.prod.yml up -d --build
