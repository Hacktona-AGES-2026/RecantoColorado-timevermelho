"""
Confere o fila_priorizada.csv além do verificador oficial: cobertura, justificativas e coerência das classes.

Uso:  python scripts/conferir_csv.py [fila_priorizada.csv] [--dataset data]
Só biblioteca padrão. Se o pandas estiver instalado, também roda o verificador oficial no fim.
"""
import argparse
import csv
import re
import subprocess
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
problemas = []


def checar(nome, ok, detalhe=""):
    print(("  OK    " if ok else "  FALHOU ") + nome + (f" — {detalhe}" if detalhe else ""))
    if not ok:
        problemas.append(nome)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("arquivo", nargs="?", default="fila_priorizada.csv")
    ap.add_argument("--dataset", default="data")
    a = ap.parse_args()
    ds = Path(a.dataset)

    fila = {r["solicitacao_id"]: r for r in csv.DictReader(open(ds / "fila_cirurgica.csv", encoding="utf-8"))}
    eventos = list(csv.DictReader(open(ds / "eventos_clinicos.csv", encoding="utf-8")))
    saida = list(csv.DictReader(open(a.arquivo, encoding="utf-8")))
    ids = [r["solicitacao_id"] for r in saida]
    cid = lambda s: fila[s]["cid_principal"].upper().replace(".", "")

    print(f"\nArquivo: {a.arquivo} · {len(saida)} linhas · fila original: {len(fila)}\n")
    print("1. Cobertura")
    checar("nenhuma solicitação repetida", len(ids) == len(set(ids)))
    checar("todas existem no dataset", all(i in fila for i in ids))
    grupos = defaultdict(list)
    for s, r in fila.items():
        grupos[(r["paciente_id"], r["procedimento_id"])].append(r)
    principal = {}
    for g in grupos.values():
        g.sort(key=lambda r: (r["data_entrada_fila"], r["solicitacao_id"]))
        for r in g:
            principal[r["solicitacao_id"]] = g[0]["solicitacao_id"]
    fora = set(fila) - set(ids)
    sem_explicacao = [s for s in fora if principal[s] == s or principal[s] not in ids]
    checar("toda solicitação fora do arquivo é duplicata com a principal no arquivo", not sem_explicacao,
           f"{len(fora)} fora; sem explicação: {sem_explicacao[:5]}")

    print("\n2. Posições")
    pos = [int(r["posicao"]) for r in saida]
    checar("posições inteiras de 1 a N, sem buraco", sorted(pos) == list(range(1, len(pos) + 1)))
    checar("arquivo já vem na ordem das posições", pos == sorted(pos))

    print("\n3. Justificativas")
    padrao = re.compile(r"^(A1|A2|B|C|D) \(prazo (15|21|60|180|360) dias\) · .+ · \d+ dias de fila \(\d+% do prazo\) · score \d+")
    ruins = [r["solicitacao_id"] for r in saida if not padrao.search(r["justificativa"])]
    checar("todas têm classe, prazo, motivo, tempo de espera e score", not ruins, f"fora do padrão: {ruins[:5]}")
    curtas = [r["solicitacao_id"] for r in saida if len(r["justificativa"]) < 60]
    checar("nenhuma justificativa curta demais", not curtas, str(curtas[:5]))
    classe = {r["solicitacao_id"]: r["justificativa"].split(" ")[0] for r in saida}
    print("     distribuição:", dict(sorted(Counter(classe.values()).items())))

    print("\n4. Coerência clínica")
    onco = [s for s in ids if cid(s).startswith("C")]
    checar("todo CID C* está em A1 ou A2", all(classe[s] in ("A1", "A2") for s in onco), f"{len(onco)} oncológicos")
    bx = {e["solicitacao_id"] for e in eventos if e["tipo_evento"] == "biopsia" and e["resultado"] == "positivo"}
    bx_benigno = [s for s in ids if s in bx and not cid(s).startswith("C")]
    checar("toda biópsia positiva com CID benigno está em A1 ou A2",
           all(classe[s] in ("A1", "A2") for s in bx_benigno), f"{len(bx_benigno)} pacientes")
    ultimo_a = max(int(r["posicao"]) for r in saida if classe[r["solicitacao_id"]] in ("A1", "A2"))
    primeiro_cd = min(int(r["posicao"]) for r in saida if classe[r["solicitacao_id"]] in ("C", "D"))
    checar("nenhum C ou D acima de um A1/A2", ultimo_a < primeiro_cd, f"último A: {ultimo_a}, primeiro C/D: {primeiro_cd}")
    sugeridas = [r for r in saida if "saída sugerida aguardando o regulador" in r["justificativa"]]
    checar("saídas sugeridas continuam no arquivo, com aviso", len(sugeridas) > 0, f"{len(sugeridas)} pacientes")

    print("\n5. Amostra para leitura humana")
    for s in ("SOL-2026-00351", ids[0], ids[len(ids) // 2], ids[-1]):
        r = next(x for x in saida if x["solicitacao_id"] == s)
        print(f"     {r['posicao']:>4}  {s}: {r['justificativa'][:160]}")

    print("\n6. Verificador oficial")
    try:
        import pandas  # noqa: F401
        r = subprocess.run([sys.executable, str(ds / "verificar_fila_priorizada.py"), a.arquivo, "--dataset", str(ds)],
                           capture_output=True, text=True, encoding="utf-8", errors="replace")
        ultima = [l for l in r.stdout.splitlines() if l.startswith("RESULTADO")]
        checar("verificar_fila_priorizada.py", r.returncode == 0, ultima[0] if ultima else r.stdout[-200:])
    except ImportError:
        print("     (pandas não instalado: rode à parte  python data/verificar_fila_priorizada.py fila_priorizada.csv --dataset data)")

    print()
    if problemas:
        print(f"{len(problemas)} problema(s): " + "; ".join(problemas))
        sys.exit(1)
    print("CSV conferido: cobertura, posições, justificativas e coerência clínica em ordem.")


if __name__ == "__main__":
    main()
