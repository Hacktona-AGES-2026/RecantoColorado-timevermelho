#!/usr/bin/env bash
# Procura chaves de API e outros segredos no repositório: nos arquivos de hoje (versionados e novos) e em TODO o
# histórico do Git. Falha (código 1) se achar algo. Rode antes de cada commit/push e antes de abrir PR.
#
#   bash scripts/verificar-segredos.sh
#
# Chaves de mentira usadas nos testes têm o prefixo "sk-teste-" e são ignoradas de propósito.
# Se uma chave REAL aparecer aqui: revogue-a no painel do provedor primeiro e só depois limpe o histórico.
set -u
cd "$(git rev-parse --show-toplevel)"

PADROES=(
  '(^|[^A-Za-z0-9_-])sk-(proj-|svcacct-|admin-)?[A-Za-z0-9_-]{20,}'  # OpenAI e compatíveis
  '(^|[^A-Za-z0-9_-])sk-ant-[A-Za-z0-9_-]{20,}'                # Anthropic
  'AKIA[0-9A-Z]{16}'                                        # AWS
  'AIza[0-9A-Za-z_-]{35}'                                   # Google
  'gh[pousr]_[A-Za-z0-9]{36,}'                              # GitHub
  'xox[baprs]-[A-Za-z0-9-]{10,}'                            # Slack
  '-----BEGIN ([A-Z]+ )?PRIVATE KEY-----'                   # chaves privadas
  '(IA_PROVEDOR_[0-9]+_CHAVE|OPENAI_API_KEY|ANTHROPIC_API_KEY)[ ]*[=:][ ]*[^ $#{"][^ ]{8,}'  # variável preenchida
  'NEXT_PUBLIC_[A-Z_]*(KEY|SECRET|TOKEN)[A-Z_]*[ ]*='      # segredo exposto ao navegador
)
REGEX=$(IFS='|'; echo "${PADROES[*]}")

achou=0

echo "== Arquivos atuais (versionados e novos, sem os ignorados) =="
resultado=$(git grep -nIE --untracked "$REGEX" -- . ':!package-lock.json' ':!scripts/verificar-segredos.sh' | grep -v 'sk-teste-')
if [ -n "$resultado" ]; then echo "$resultado" | sed -E 's/(.{0,12}).{0,200}/\1 ...[linha omitida: pode conter segredo]/' ; achou=1; else echo "nada encontrado"; fi

echo "== Histórico do Git (todas as branches) =="
if [ -n "$(git rev-list --all | head -1)" ]; then
  hist=$(git rev-list --all | xargs git grep -nIE "$REGEX" -- . ':!package-lock.json' ':!scripts/verificar-segredos.sh' 2>/dev/null | grep -v 'sk-teste-' | cut -d: -f1,2 | sort -u)
  if [ -n "$hist" ]; then echo "$hist" | sed 's/^/commit:arquivo  /' | head -20; achou=1; else echo "nada encontrado"; fi
else
  echo "(sem commits)"
fi

echo "== Build do frontend (o que o navegador recebe) =="
if [ -d frontend/.next ]; then
  # chaves, nomes das variáveis de chave e endereços dos provedores de IA não podem estar no JavaScript do build
  build=$(grep -rIlE '(^|[^A-Za-z0-9_-])sk-(proj-|svcacct-|admin-)?[A-Za-z0-9_-]{20,}|(^|[^A-Za-z0-9_-])sk-ant-[A-Za-z0-9_-]{20,}|IA_PROVEDOR_[0-9]|OPENAI_API_KEY|ANTHROPIC_API_KEY|api\.openai\.com|api\.anthropic\.com' frontend/.next/static frontend/.next/server 2>/dev/null | head -10)
  if [ -n "$build" ]; then echo "ENCONTRADO no build do frontend:"; echo "$build"; achou=1; else echo "nada encontrado em frontend/.next"; fi
else
  echo "(sem frontend/.next: rode 'npm run build' no frontend para conferir o build)"
fi

echo "== .gitignore protege arquivos de chave? =="
for padrao in ".env" ".env.local" "*.pem" "*.key"; do
  if git check-ignore -q "x/$padrao" 2>/dev/null || git check-ignore -q "$padrao" 2>/dev/null; then
    echo "ok: $padrao é ignorado"
  else
    echo "FALTA no .gitignore: $padrao"; achou=1
  fi
done

if [ "$achou" -ne 0 ]; then echo; echo "ATENÇÃO: veja os itens acima antes de seguir."; exit 1; fi
echo; echo "Tudo certo."
