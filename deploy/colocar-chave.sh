#!/bin/bash
# Grava a chave da IA no servidor (backend/.env) e reinicia o backend.
# Uso, na máquina:  cd /opt/filaviva && git pull -q && bash deploy/colocar-chave.sh
# Aguenta a colagem do terminal do navegador: tira códigos invisíveis e espaços, e se a chave vier repetida
# (ex.: colada duas vezes) usa só a primeira. A chave nunca é mostrada; só o tamanho.
set -uo pipefail
cd "$(dirname "$0")/.."

printf '\e[?2004l'   # desliga a "colagem marcada" do terminal, que mistura códigos na chave
echo "Cole a chave da IA UMA vez e aperte Enter (ela nao aparece na tela):"
IFS= read -rs BRUTO
echo

# só letras, números, _ e -; depois, a primeira chave a partir de "sk-" até a próxima repetição
LIMPO=$(printf '%s' "$BRUTO" | tr -cd 'A-Za-z0-9_-')
unset BRUTO
case "$LIMPO" in
  *sk-*) RESTO=${LIMPO#*sk-}; CHAVE="sk-${RESTO%%sk-*}" ;;
  *) CHAVE="" ;;
esac
unset LIMPO RESTO

if [ ${#CHAVE} -lt 40 ]; then
  echo "ERRO: nao reconheci uma chave (precisa comecar com sk-). Nada foi alterado. Rode de novo."
  exit 1
fi

umask 077
printf 'IA_PROVEDOR_1_CHAVE=%s\n' "$CHAVE" > backend/.env
echo "Chave gravada. Tamanho: ${#CHAVE} caracteres."
unset CHAVE

sudo docker compose -f docker-compose.prod.yml up -d --force-recreate backend >/dev/null 2>&1 \
  && echo "PRONTO: backend reiniciado. Em cerca de 30 segundos o assistente liga." \
  || echo "ERRO ao reiniciar o backend: rode  sudo docker compose -f docker-compose.prod.yml up -d --force-recreate backend"
