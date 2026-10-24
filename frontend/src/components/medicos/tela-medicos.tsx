"use client";

// Cadastro de médicos: cada médico tem uma especialidade, que define a lista de pacientes dele.
import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { ArrowRightIcon, Trash2Icon, UserPlusIcon } from "lucide-react";
import { api, type Especialidade, type Medico } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { CampoSelect, Carregando, ErroApi, mensagemErro, Vazio } from "@/components/fila/comum";

export function TelaMedicos() {
  const [medicos, setMedicos] = useState<Medico[] | null>(null);
  const [especialidades, setEspecialidades] = useState<Especialidade[]>([]);
  const [erro, setErro] = useState<string | null>(null);

  const [versao, setVersao] = useState(0);
  const carregar = useCallback(async () => setVersao((v) => v + 1), []);

  useEffect(() => {
    let ativo = true;
    Promise.all([api.medicos(), api.especialidades()])
      .then(([m, e]) => {
        if (!ativo) return;
        setMedicos(m);
        setEspecialidades(e);
        setErro(null);
      })
      .catch((e) => ativo && setErro(mensagemErro(e)));
    return () => {
      ativo = false;
    };
  }, [versao]);

  const pacientesPorEsp = useMemo(
    () => Object.fromEntries(especialidades.map((e) => [e.codigo, e])),
    [especialidades],
  );

  async function remover(m: Medico) {
    if (!window.confirm(`Remover ${m.nome} (${m.crm})?`)) return;
    try {
      await api.removerMedico(m.id);
      await carregar();
    } catch (e) {
      setErro(mensagemErro(e));
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-4 px-4 py-6">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">Médicos</h1>
        <p className="text-muted-foreground text-sm">
          Cada médico vê a fila completa, a lista dos pacientes cujo procedimento cirúrgico é da sua especialidade e, à
          parte, os pacientes de outras especialidades que podem precisar de avaliação dele. Tudo do mais grave para o
          menos grave, na mesma ordem da fila geral.
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-[1fr_380px]">
        <Card>
          <CardHeader>
            <CardTitle>Cadastrados</CardTitle>
            <CardDescription>Clique em um médico para abrir as listas dele.</CardDescription>
          </CardHeader>
          <CardContent>
            {erro && !medicos ? (
              <ErroApi erro={erro} aoTentar={carregar} />
            ) : !medicos ? (
              <Carregando texto="Carregando médicos…" />
            ) : medicos.length === 0 ? (
              <Vazio texto="Nenhum médico cadastrado ainda." />
            ) : (
              <ul className="divide-y">
                {medicos.map((m) => (
                  <li key={m.id} className="flex items-center gap-3 py-2.5">
                    <Link href={`/medicos/${m.id}`} className="group flex min-w-0 flex-1 items-center gap-3">
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-medium group-hover:underline">{m.nome}</p>
                        <p className="text-muted-foreground truncate text-xs">
                          {m.crm} · {m.especialidadeNome}
                        </p>
                      </div>
                      <span className="text-muted-foreground shrink-0 text-right text-xs tabular-nums">
                        {pacientesPorEsp[m.especialidade]?.pacientesCirurgicos ?? 0} cirúrgicos
                        <span className="block">
                          {pacientesPorEsp[m.especialidade]?.pacientesPossivelAvaliacao ?? 0} possível avaliação
                        </span>
                      </span>
                      <ArrowRightIcon className="text-muted-foreground size-4 shrink-0" />
                    </Link>
                    <Button variant="ghost" size="icon-sm" aria-label={`Remover ${m.nome}`} onClick={() => remover(m)}>
                      <Trash2Icon className="size-4" />
                    </Button>
                  </li>
                ))}
              </ul>
            )}
            {erro && medicos && <p className="text-destructive mt-2 text-sm">{erro}</p>}
          </CardContent>
        </Card>

        <FormMedico especialidades={especialidades} aoCadastrar={carregar} />
      </div>
    </div>
  );
}

function FormMedico({ especialidades, aoCadastrar }: { especialidades: Especialidade[]; aoCadastrar: () => Promise<void> }) {
  const [nome, setNome] = useState("");
  const [crm, setCrm] = useState("");
  const [especialidade, setEspecialidade] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [mensagem, setMensagem] = useState<{ ok: boolean; texto: string } | null>(null);

  const escolhida = especialidades.find((e) => e.codigo === especialidade);

  async function enviar(ev: React.FormEvent) {
    ev.preventDefault();
    setMensagem(null);
    if (!nome.trim() || !crm.trim() || !especialidade) {
      setMensagem({ ok: false, texto: "Preencha nome, CRM e especialidade." });
      return;
    }
    setEnviando(true);
    try {
      const m = await api.cadastrarMedico({ nome: nome.trim(), crm: crm.trim(), especialidade });
      setMensagem({ ok: true, texto: `${m.nome} cadastrado em ${m.especialidadeNome}.` });
      setNome("");
      setCrm("");
      setEspecialidade("");
      await aoCadastrar();
    } catch (e) {
      setMensagem({ ok: false, texto: mensagemErro(e) });
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Cadastrar médico</CardTitle>
        <CardDescription>A especialidade decide quais pacientes entram na lista dele.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={enviar} className="flex flex-col gap-3">
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium">Nome</span>
            <Input value={nome} onChange={(e) => setNome(e.target.value)} placeholder="Dra. Maria Souza" maxLength={120} />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium">CRM</span>
            <Input value={crm} onChange={(e) => setCrm(e.target.value)} placeholder="CRM-RS 12345" maxLength={30} />
          </label>
          <div className="flex flex-col gap-1 text-sm">
            <span className="font-medium">Especialidade</span>
            <CampoSelect
              rotulo="Especialidade"
              valor={especialidade}
              aoMudar={setEspecialidade}
              opcoes={[
                { valor: "", rotulo: "Escolha…" },
                ...especialidades.map((e) => ({ valor: e.codigo, rotulo: `${e.nome} (${e.pacientesCirurgicos})` })),
              ]}
            />
            {escolhida && (
              <p className="text-muted-foreground text-xs">
                {escolhida.descricao} Agora: {escolhida.pacientesCirurgicos} paciente(s) com procedimento da especialidade e{" "}
                {escolhida.pacientesPossivelAvaliacao} para possível avaliação.
              </p>
            )}
          </div>
          <Button type="submit" disabled={enviando}>
            <UserPlusIcon className="size-4" />
            {enviando ? "Cadastrando…" : "Cadastrar"}
          </Button>
          {mensagem && (
            <p className={mensagem.ok ? "text-sm text-emerald-700 dark:text-emerald-400" : "text-destructive text-sm"}>
              {mensagem.texto}
            </p>
          )}
        </form>
      </CardContent>
    </Card>
  );
}
