"""
Teste de integração do FilaVIVA contra o sistema real (Postgres + backend + frontend no ar).

Percorre os fluxos principais da demo e diz o que passou e o que falhou.
No fim chama /api/demo/reset, então a demo fica limpa para apresentar.

Uso (com tudo rodando):  python scripts/teste_integracao.py
Opções:                  --api http://localhost:8080  --front http://localhost:3000
Só biblioteca padrão do Python; não precisa instalar nada.
"""
import argparse
import csv
import io
import json
import sys
import urllib.error
import urllib.request

# o console do Windows não usa UTF-8 por padrão; sem isso, acentos e setas quebram o print
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

PACIENTE_DEMO = "SOL-2026-00351"  # CID D44.0, prioridade "baixa", biópsia positiva
falhas = []
cabecalhos = {}  # cabeçalhos da última resposta (ex.: X-FilaViva-Snapshot)


def pedir(metodo, url, corpo=None):
    dados = None if corpo is None else json.dumps(corpo).encode("utf-8")
    req = urllib.request.Request(url, data=dados, method=metodo, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            cabecalhos.clear()
            cabecalhos.update({k.lower(): v for k, v in r.headers.items()})
            bruto = r.read().decode("utf-8")
            return r.status, (json.loads(bruto) if "json" in r.headers.get("Content-Type", "") else bruto)
    except urllib.error.HTTPError as e:
        bruto = e.read().decode("utf-8")
        try:
            return e.code, json.loads(bruto)
        except json.JSONDecodeError:
            return e.code, bruto


def checar(nome, condicao, detalhe=""):
    print(("  OK    " if condicao else "  FALHOU ") + nome + (f" — {detalhe}" if detalhe else ""))
    if not condicao:
        falhas.append(nome)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--api", default="http://localhost:8080")
    ap.add_argument("--front", default="http://localhost:3000")
    a = ap.parse_args()
    api = a.api.rstrip("/") + "/api"

    print("\n1. Backend e dados")
    try:
        st, s = pedir("GET", f"{api}/status")
    except urllib.error.URLError as e:
        print(f"  Backend fora do ar em {a.api}: {e}. Rode o backend e tente de novo.")
        sys.exit(2)
    checar("API no ar e dataset encontrado", st == 200 and s.get("datasetEncontrado"))
    pedir("POST", f"{api}/demo/reset")  # começa do estado do dataset
    st, f = pedir("GET", f"{api}/fila?tamanho=500")
    checar("fila tem os 919 casos (941 menos 22 duplicatas)", f["total"] == 919, f"total={f['total']}")
    pos = [i["posicao"] for i in f["itens"]]
    checar("posições começam em 1 e são sequenciais", pos == list(range(1, len(pos) + 1)))
    checar("contagem por classe bate com o total", sum(f["porClasse"].values()) == f["total"], str(f["porClasse"]))

    print("\n2. Regra de ouro e CSV")
    st, texto = pedir("GET", f"{api}/fila/export.csv")
    linhas = list(csv.DictReader(io.StringIO(texto)))
    checar("export.csv tem cabeçalho e 919 linhas", st == 200 and len(linhas) == 919, f"{len(linhas)} linhas")
    checar("nenhuma justificativa vazia", all(l["justificativa"].strip() for l in linhas))
    checar("o download padrão é a fila de referência (entrega oficial)",
           cabecalhos.get("x-filaviva-snapshot", "").startswith("oficial")
           and 'filename="fila_priorizada.csv"' in cabecalhos.get("content-disposition", ""),
           cabecalhos.get("x-filaviva-snapshot", ""))
    oficial_inicial = texto
    st, s = pedir("GET", f"{api}/status")
    checar("status mostra os dois snapshots, com versão e horário",
           {"operacional", "oficial"} <= set(s.get("snapshots", {}))
           and all(v.get("versao") and v.get("calculadoEm") for v in s["snapshots"].values()),
           ", ".join(f"{k}={v.get('versao')}" for k, v in s.get("snapshots", {}).items()))
    st, ind = pedir("GET", f"{api}/indicadores")
    viol = next(c for c in ind["comparacoes"] if "abaixo de um paciente sem urgência" in c["indicador"])
    checar("zero oncológicos abaixo de um paciente sem urgência", viol["depois"] == "0", f"hoje={viol['antes']}")

    print(f"\n3. Paciente da demo ({PACIENTE_DEMO})")
    st, p = pedir("GET", f"{api}/pacientes/{PACIENTE_DEMO}")
    it = p["item"]
    checar("classe A2 por biópsia positiva", it["classe"] == "A2", it["classe"])
    checar("sobe em relação à ordem de hoje", it["posicao"] < it["posicaoManual"],
           f"hoje {it['posicaoManual']} → FilaVIVA {it['posicao']}")
    checar("evidência do laudo citada", any("NEOPLASIA MALIGNA" in (d.get("evidencia") or "")
                                            for d in p["classificacao"]["determinantes"]))
    posicao_antes = it["posicao"]

    print("\n4. Evento novo recalcula a fila")
    st, imp = pedir("POST", f"{api}/eventos", {"solicitacaoId": PACIENTE_DEMO, "tipo": "exame_imagem",
                                                "resultado": "progressao", "descricao": "TC: aumento da lesão (teste)"})
    checar("evento registrado", st == 200, imp.get("mensagem", "") if isinstance(imp, dict) else str(imp))
    checar("progressão leva a A1 e sobe na fila", imp.get("classeDepois") == "A1"
           and imp.get("posicaoDepois", 10**6) < posicao_antes, f"{posicao_antes} → {imp.get('posicaoDepois')}")
    st, al = pedir("GET", f"{api}/alertas")
    checar("alerta do evento aparece em 'O que mudou'", any(x["solicitacaoId"] == PACIENTE_DEMO for x in al["recentes"]))

    print("\n5. Decisões do regulador")
    onco = next(i for i in f["itens"] if i["cid"].startswith("C"))
    st, r = pedir("POST", f"{api}/ajustes", {"solicitacaoId": onco["solicitacaoId"], "tipo": "ALTERAR_CLASSE",
                                             "classeNova": "D", "justificativa": "teste de rebaixamento indevido"})
    checar("rebaixar oncológico é recusado (422)", st == 422 and "Regra de ouro" in r.get("mensagem", ""), str(st))
    st, r = pedir("POST", f"{api}/ajustes", {"solicitacaoId": onco["solicitacaoId"], "tipo": "CONFIRMAR_CLASSE",
                                             "justificativa": "curta"})
    checar("justificativa curta é recusada (400)", st == 400, str(st))
    st, r = pedir("POST", f"{api}/ajustes", {"solicitacaoId": onco["solicitacaoId"], "tipo": "CONFIRMAR_CLASSE",
                                             "justificativa": "conferido com o laudo e o cirurgião", "autor": "teste"})
    checar("confirmar classe com justificativa funciona", st == 200, r.get("mensagem", ""))
    st, det = pedir("GET", f"{api}/pacientes/{onco['solicitacaoId']}")
    checar("decisão entra no histórico de auditoria", any(x["autor"] == "teste" for x in det["ajustes"]))

    print("\n6. Saída sugerida só sai com decisão humana")
    st, sug = pedir("GET", f"{api}/fila?status=SAIDA_SUGERIDA&tamanho=100")
    checar("existem saídas sugeridas", sug["total"] > 0, f"{sug['total']}")
    checar("todas continuam na fila, com posição", all(i["posicao"] for i in sug["itens"]))
    alvo = sug["itens"][0]
    st, r = pedir("POST", f"{api}/ajustes", {"solicitacaoId": alvo["solicitacaoId"], "tipo": "CONFIRMAR_SAIDA",
                                             "justificativa": "paciente confirmou por telefone (teste)"})
    checar("regulador confirma a saída", st == 200 and r.get("statusDepois") == "SAIDA_CONFIRMADA", r.get("mensagem", ""))
    st, f2 = pedir("GET", f"{api}/fila?tamanho=1")
    checar("fila diminui em 1 depois da confirmação", f2["total"] == 918, f"total={f2['total']}")

    print("\n6b. Regra de ouro na fila operacional: promoção sem evidência × com evidência nova")

    def fila_toda():
        todos = []
        for pagina in range(2):
            todos += pedir("GET", f"{api}/fila?tamanho=500&pagina={pagina}")[1]["itens"]
        return todos

    def oncos_abaixo_de(sid):
        todos = fila_toda()
        pos = next(i["posicao"] for i in todos if i["solicitacaoId"] == sid)
        return pos, [i for i in todos if i["cid"].startswith("C") and i["posicao"] > pos]

    st, d = pedir("GET", f"{api}/fila?classe=D&tamanho=50")
    calmos = [i["solicitacaoId"] for i in d["itens"] if not i["oncologico"] and i["status"] == "ATIVA"][:2]
    sem_evid, com_evid = calmos
    # (a) promoção sem nenhuma evidência clínica: não contorna a regra
    pedir("POST", f"{api}/ajustes", {"solicitacaoId": sem_evid, "tipo": "ALTERAR_CLASSE", "classeNova": "A1",
                                     "justificativa": "teste de promoção sem evidência"})
    pos, abaixo = oncos_abaixo_de(sem_evid)
    checar("promoção sem evidência não passa à frente de nenhum câncer", not abaixo,
           f"posição {pos}, oncológicos abaixo: {len(abaixo)}")
    # (b) progressão registrada agora + promoção: a urgência nova é considerada, sem bloqueio artificial
    pedir("POST", f"{api}/eventos", {"solicitacaoId": com_evid, "tipo": "exame_imagem", "resultado": "progressao",
                                     "descricao": "piora em imagem (teste)"})
    pedir("POST", f"{api}/ajustes", {"solicitacaoId": com_evid, "tipo": "ALTERAR_CLASSE", "classeNova": "A1",
                                     "justificativa": "teste de promoção com evidência nova"})
    pos, abaixo = oncos_abaixo_de(com_evid)
    checar("com evidência nova, o paciente urgente pode passar à frente de cânceres", len(abaixo) > 0,
           f"posição {pos}, oncológicos abaixo: {len(abaixo)}")
    st, ind = pedir("GET", f"{api}/indicadores")
    viol = next(c for c in ind["comparacoes"] if "abaixo de um paciente sem urgência" in c["indicador"])
    checar("regra de ouro em zero na fila operacional", viol["depois"] == "0", viol["depois"])
    # (c) a fila operacional e o CSV operacional são o mesmo snapshot
    st, pag = pedir("GET", f"{api}/fila?tamanho=1")
    versao_op = pag["snapshot"]["versao"]
    st, texto_op = pedir("GET", f"{api}/fila/export.csv?snapshot=operacional&versao={versao_op}")
    checar("CSV operacional traz a mesma versão da tela", st == 200 and f"versao={versao_op}"
           in cabecalhos.get("x-filaviva-snapshot", "") and "fila_operacional_" in cabecalhos.get("content-disposition", ""),
           cabecalhos.get("x-filaviva-snapshot", ""))
    linhas_op = list(csv.DictReader(io.StringIO(texto_op)))
    tela = fila_toda()
    checar("CSV operacional = fila da tela (ordem, posições e justificativas)",
           [(l["solicitacao_id"], int(l["posicao"]), l["justificativa"]) for l in linhas_op]
           == [(i["solicitacaoId"], i["posicao"], i["justificativa"]) for i in tela])
    st, _ = pedir("GET", f"{api}/fila/export.csv?snapshot=operacional&versao=op-0")
    checar("versão desatualizada é recusada (409), sem divergência silenciosa", st == 409, str(st))
    # (d) a fila de referência não mudou com nada disso
    st, texto_of = pedir("GET", f"{api}/fila/export.csv?snapshot=oficial")
    checar("fila de referência continua igual depois de eventos e decisões", texto_of == oficial_inicial)

    print("\n7. Simulações não gravam nada")
    st, d = pedir("GET", f"{api}/fila?classe=D&tamanho=50")
    calmo = next(i for i in d["itens"] if not i["oncologico"] and i["status"] == "ATIVA")
    st, antes_sim = pedir("GET", f"{api}/fila?tamanho=1")
    st, sim = pedir("POST", f"{api}/simulacao", {"solicitacaoId": calmo["solicitacaoId"], "dor": 9})
    checar("dor 9 hipotética leva para B", st == 200 and sim["depois"]["classe"] == "B", sim.get("mensagem", ""))
    st, depois = pedir("GET", f"{api}/pacientes/{calmo['solicitacaoId']}")
    checar("paciente real continua igual", depois["item"]["classe"] == "D")
    st, depois_sim = pedir("GET", f"{api}/fila?tamanho=1")
    checar("simulação não cria nova versão da fila operacional",
           depois_sim["snapshot"]["versao"] == antes_sim["snapshot"]["versao"], depois_sim["snapshot"]["versao"])
    st, pes = pedir("POST", f"{api}/simulacao/pesos", {"diagnostico": 30, "piora": 25, "dor": 15, "limitacao": 10,
                                                       "espera": 50, "risco": 5})
    checar("simulação de pesos respeita a regra de ouro", st == 200 and pes["regraDeOuroRespeitada"],
           f"{pes.get('pacientesQueMudaram')} mudaram")

    print("\n8. Cenário de demo e reset")
    st, cen = pedir("POST", f"{api}/demo/cenario")
    checar("cenário de demo gera impactos", st == 200 and len(cen) >= 3, f"{len(cen)} eventos")
    st, _ = pedir("POST", f"{api}/demo/reset")
    st, al = pedir("GET", f"{api}/alertas")
    st, f3 = pedir("GET", f"{api}/fila?tamanho=1")
    checar("reset zera alertas e decisões", not al["recentes"] and f3["total"] == 919)
    st, p = pedir("GET", f"{api}/pacientes/{PACIENTE_DEMO}")
    checar("paciente da demo volta à posição original", p["item"]["posicao"] == posicao_antes, str(p["item"]["posicao"]))

    print("\n9. Telas do frontend")
    for rota in ["/", f"/paciente/{PACIENTE_DEMO}", "/alertas", "/simulador", "/indicadores"]:
        try:
            st, _ = pedir("GET", a.front.rstrip("/") + rota)
        except urllib.error.URLError as e:
            st = str(e)
        checar(f"tela {rota} responde", st == 200, str(st))

    print()
    if falhas:
        print(f"{len(falhas)} verificação(ões) falharam: " + "; ".join(falhas))
        sys.exit(1)
    print("Tudo certo. A demo foi reiniciada e está limpa.")


if __name__ == "__main__":
    main()
