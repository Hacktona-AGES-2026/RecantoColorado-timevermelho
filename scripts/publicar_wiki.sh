#!/usr/bin/env bash
# Publica a pasta wiki/ deste repositório na wiki do GitHub (repositório <repo>.wiki.git).
# A wiki é mantida junto com o código: edite wiki/*.md no mesmo commit da mudança e rode este script depois do push.
#
# Uso:  bash scripts/publicar_wiki.sh
# Requer: repositório público (ou plano pago) com a wiki habilitada e a primeira página criada pela interface do GitHub
# (o GitHub só cria o repositório .wiki.git depois disso).
set -euo pipefail

raiz="$(cd "$(dirname "$0")/.." && pwd)"
origem="$(git -C "$raiz" remote get-url origin)"
wiki_url="${origem%.git}.wiki.git"
commit="$(git -C "$raiz" rev-parse --short HEAD)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

if ! git clone -q "$wiki_url" "$tmp/wiki" 2>/dev/null; then
  echo "Não consegui clonar $wiki_url."
  echo "Habilite a wiki em Settings > Features e crie a primeira página pela interface do GitHub."
  echo "Em repositório privado de organização no plano gratuito, a wiki só fica disponível com o repositório público."
  exit 1
fi

# espelha wiki/ (inclusive remoções de páginas), sem mexer no .git da wiki
find "$tmp/wiki" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
cp -R "$raiz/wiki/." "$tmp/wiki/"

cd "$tmp/wiki"
git add -A
if git diff --cached --quiet; then
  echo "Wiki já está atualizada (código em $commit)."
  exit 0
fi
git commit -q -m "Atualiza a wiki com o código em $commit"
git push -q origin HEAD
echo "Wiki publicada a partir do código em $commit."
