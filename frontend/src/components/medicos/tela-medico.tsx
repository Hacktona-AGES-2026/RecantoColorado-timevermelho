"use client";

// Tela do médico: pacientes cirúrgicos da especialidade (atribuídos), possível avaliação (só sinalizados) e fila completa.
import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeftIcon, ChevronLeftIcon, ChevronRightIcon, InfoIcon, RefreshCwIcon, StethoscopeIcon } from "lucide-react";
import { api, CLASSES, type ClasseSwalis, type Especialidade, type FilaMedico, type GrupoMedico, type Medico } from "@/lib/api";
import { BarraPrazo, SeloClasse } from "@/components/fila-ui";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { CampoSelect, Carregando, ErroApi, mensagemErro, Vazio } from "@/components/fila/comum";
import { TelaFila } from "@/components/fila/tela-fila";

const TAMANHO = 50;

export function TelaMedico() {
  const { id } = useParams<{ id: string }>();
  // cabeçalho e contadores das abas vêm da primeira resposta da lista cirúrgica
  const [cabecalho, setCabecalho] = useState<{ medico: Medico; especialidade: Especialidade; totalFilaGeral: number } | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    let ativo = true;
    api
      .filaMedico(id, { grupo: "CIRURGICO", tamanho: 1 })
      .then((d) => ativo && setCabecalho({ medico: d.medico, especialidade: d.especialidade, totalFilaGeral: d.totalFilaGeral }))
      .catch((e) => ativo && setErro(mensagemErro(e)));
    return () => {
      ativo = false;
    };
  }, [id]);

  if (erro && !cabecalho) return <ErroApi erro={erro} />;
  if (!cabecalho) return <Carregando texto="Carregando o médico…" />;
  const { medico, especialidade } = cabecalho;

  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-4 px-4 py-6">
      <div className="flex flex-wrap items-start gap-3">
        <Link href="/medicos" className="text-muted-foreground hover:text-foreground mt-1" aria-label="Voltar para médicos">
          <ArrowLeftIcon className="size-4" />
        </Link>
        <div className="min-w-0 flex-1">
          <h1 className="flex items-center gap-2 text-xl font-semibold tracking-tight">
            <StethoscopeIcon className="size-5" />
            {medico.nome}
          </h1>
          <p className="text-muted-foreground text-sm">
            {medico.crm} · {especialidade.nome}
          </p>
        </div>
      </div>

      <Tabs defaultValue="CIRURGICO" className="gap-4">
        <TabsList className="h-auto flex-wrap">
          <TabsTrigger value="CIRURGICO" className="px-3">
            Pacientes cirúrgicos ({especialidade.pacientesCirurgicos})
          </TabsTrigger>
          <TabsTrigger value="POSSIVEL_AVALIACAO" className="px-3">
            Possível avaliação da especialidade ({especialidade.pacientesPossivelAvaliacao})
          </TabsTrigger>
          <TabsTrigger value="geral" className="px-3">
            Fila completa ({cabecalho.totalFilaGeral})
          </TabsTrigger>
        </TabsList>

        <TabsContent value="CIRURGICO">
          <ListaGrupo medicoId={id} grupo="CIRURGICO" especialidade={especialidade} />
        </TabsContent>
        <TabsContent value="POSSIVEL_AVALIACAO">
          <ListaGrupo medicoId={id} grupo="POSSIVEL_AVALIACAO" especialidade={especialidade} />
        </TabsContent>
        <TabsContent value="geral">
          <TelaFila />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function ListaGrupo({ medicoId, grupo, especialidade }: { medicoId: string; grupo: GrupoMedico; especialidade: Especialidade }) {
  const [dados, setDados] = useState<FilaMedico | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [busca, setBusca] = useState("");
  const [buscaAplicada, setBuscaAplicada] = useState("");
  const [classe, setClasse] = useState<ClasseSwalis | "">("");
  const [pagina, setPagina] = useState(0);
  const [versao, setVersao] = useState(0);

  const carregar = useCallback(() => {
    setCarregando(true);
    setVersao((v) => v + 1);
  }, []);

  useEffect(() => {
    let ativo = true;
    api
      .filaMedico(medicoId, { grupo, busca: buscaAplicada, classe, pagina, tamanho: TAMANHO })
      .then((d) => {
        if (!ativo) return;
        setDados(d);
        setErro(null);
      })
      .catch((e) => ativo && setErro(mensagemErro(e)))
      .finally(() => ativo && setCarregando(false));
    return () => {
      ativo = false;
    };
  }, [medicoId, grupo, buscaAplicada, classe, pagina, versao]);

  // busca com pequena espera para não chamar a API a cada tecla
  useEffect(() => {
    const t = setTimeout(() => {
      setBuscaAplicada(busca);
      setPagina(0);
    }, 300);
    return () => clearTimeout(t);
  }, [busca]);

  if (erro && !dados) return <ErroApi erro={erro} aoTentar={carregar} />;
  if (!dados) return <Carregando texto="Carregando a lista…" />;

  const avaliacao = grupo === "POSSIVEL_AVALIACAO";
  const totalGrupo = avaliacao ? especialidade.pacientesPossivelAvaliacao : especialidade.pacientesCirurgicos;
  const paginas = Math.max(1, Math.ceil(dados.total / TAMANHO));

  return (
    <div className="flex flex-col gap-3">
      {avaliacao ? (
        <div className="flex gap-2 rounded-md border border-sky-300 bg-sky-50 px-3 py-2 text-sm text-sky-950 dark:border-sky-800 dark:bg-sky-950/40 dark:text-sky-100">
          <InfoIcon className="mt-0.5 size-4 shrink-0" />
          <p>
            <strong>Possível avaliação da especialidade.</strong> Estes pacientes <strong>não</strong> estão atribuídos a
            você: o procedimento pedido é de outra especialidade. Aparecem porque o CID, o CID secundário, as comorbidades
            ou outro dado clínico se relacionam com {especialidade.nome}, e podem precisar de parecer antes da cirurgia.
            A posição, a classe e o score são os da fila geral.
          </p>
        </div>
      ) : (
        <p className="text-muted-foreground text-sm">
          Pacientes cujo procedimento cirúrgico é de {especialidade.nome}, do mais grave para o menos grave: classe SWALIS,
          score e prazo, a mesma ordem da fila geral (a regra de ouro continua valendo). “# geral” é a posição na fila
          completa.
        </p>
      )}

      <div className="flex flex-wrap gap-2">
        {CLASSES.map((c) => (
          <button
            key={c}
            type="button"
            onClick={() => {
              setClasse(classe === c ? "" : c);
              setPagina(0);
            }}
            className={`flex items-center gap-1.5 rounded-md border px-2 py-1 text-xs transition-opacity ${
              classe && classe !== c ? "opacity-40" : ""
            }`}
            aria-pressed={classe === c}
          >
            <SeloClasse classe={c} />
            <span className="tabular-nums">{dados.porClasse[c] ?? 0}</span>
          </button>
        ))}
      </div>

      <Card className="py-0">
        <CardContent className="flex flex-wrap items-center gap-2 px-3 py-3">
          <Input
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            placeholder="Buscar por nome, solicitação, procedimento ou CID"
            className="min-w-56 flex-1"
          />
          <CampoSelect
            rotulo="Classe"
            className="w-36"
            valor={classe}
            aoMudar={(v) => {
              setClasse(v as ClasseSwalis | "");
              setPagina(0);
            }}
            opcoes={[{ valor: "", rotulo: "Todas as classes" }, ...CLASSES.map((c) => ({ valor: c, rotulo: `Classe ${c}` }))]}
          />
          <Button variant="outline" size="sm" onClick={carregar} disabled={carregando}>
            <RefreshCwIcon className={`size-3.5 ${carregando ? "animate-spin" : ""}`} />
            Atualizar
          </Button>
        </CardContent>
      </Card>

      {erro && <p className="text-destructive text-sm">{erro}</p>}

      <Card className="py-0">
        {dados.itens.length === 0 ? (
          <Vazio
            texto={
              totalGrupo === 0
                ? avaliacao
                  ? `Nenhum paciente de outra especialidade com relação a ${especialidade.nome} no momento.`
                  : `Nenhum paciente com procedimento de ${especialidade.nome} na fila no momento.`
                : "Nenhum paciente com esse filtro."
            }
          />
        ) : (
          <TabelaMedico dados={dados} avaliacao={avaliacao} />
        )}
      </Card>

      {paginas > 1 && (
        <div className="flex items-center justify-end gap-2 text-sm">
          <span className="text-muted-foreground">
            Página {pagina + 1} de {paginas} · {dados.total} pacientes
          </span>
          <Button variant="outline" size="icon-sm" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)} aria-label="Página anterior">
            <ChevronLeftIcon className="size-4" />
          </Button>
          <Button variant="outline" size="icon-sm" disabled={pagina + 1 >= paginas} onClick={() => setPagina(pagina + 1)} aria-label="Próxima página">
            <ChevronRightIcon className="size-4" />
          </Button>
        </div>
      )}
    </div>
  );
}

function TabelaMedico({ dados, avaliacao }: { dados: FilaMedico; avaliacao: boolean }) {
  const router = useRouter();
  return (
    <Table className="text-[13px]">
      <TableHeader>
        <TableRow className="bg-muted/40 hover:bg-muted/40">
          <TableHead className="w-12 text-right" title="Posição nesta lista">#</TableHead>
          <TableHead className="w-16 text-right" title="Posição na fila completa">
            # geral
          </TableHead>
          <TableHead>Classe</TableHead>
          <TableHead>Paciente</TableHead>
          <TableHead>{avaliacao ? "Procedimento (outra especialidade)" : "Procedimento"}</TableHead>
          <TableHead>CID</TableHead>
          <TableHead>Espera / prazo</TableHead>
          <TableHead className="text-right">Score</TableHead>
          <TableHead>{avaliacao ? "Por que pode precisar da especialidade" : "Por que está nesta lista"}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {dados.itens.map(({ posicaoNaLista, motivosEspecialidade, item: it }) => {
          const href = `/paciente/${encodeURIComponent(it.solicitacaoId)}`;
          return (
            <TableRow key={it.solicitacaoId} className="cursor-pointer" onClick={() => router.push(href)}>
              <TableCell className="text-right font-semibold tabular-nums">{posicaoNaLista}</TableCell>
              <TableCell className="text-muted-foreground text-right tabular-nums">{it.posicao ?? "—"}</TableCell>
              <TableCell>
                <SeloClasse classe={it.classe} />
              </TableCell>
              <TableCell className="max-w-56">
                <Link href={href} className="block truncate font-medium hover:underline" onClick={(e) => e.stopPropagation()}>
                  {it.nome}
                </Link>
                <span className="text-muted-foreground text-xs">
                  {it.solicitacaoId}
                  {it.idade !== null && ` · ${it.idade} a`}
                  {it.sexo && ` · ${it.sexo}`}
                </span>
              </TableCell>
              <TableCell className="max-w-56">
                <span className="block truncate" title={it.procedimento}>
                  {it.procedimento}
                </span>
                <span className="text-muted-foreground block truncate text-xs">{it.especialidade}</span>
              </TableCell>
              <TableCell title={it.cidDescricao ?? undefined}>
                {it.oncologico ? (
                  <Badge variant="destructive" className="font-semibold">
                    {it.cid} · onco
                  </Badge>
                ) : (
                  <span className="font-mono text-xs">{it.cid}</span>
                )}
              </TableCell>
              <TableCell>
                <span className="text-xs tabular-nums">
                  {it.diasEmFila} d <span className="text-muted-foreground">/ {it.prazoDias} d</span>
                </span>
                <BarraPrazo percentual={it.percentualPrazo} />
              </TableCell>
              <TableCell className="text-right font-medium tabular-nums">{it.score.toFixed(1)}</TableCell>
              <TableCell className="max-w-72">
                <span className="block truncate text-xs" title={motivosEspecialidade.join(" · ")}>
                  {motivosEspecialidade.join(" · ")}
                </span>
                {it.motivos[0] && (
                  <span className="text-muted-foreground block truncate text-[11px]" title={it.justificativa}>
                    Gravidade: {it.motivos[0].texto}
                  </span>
                )}
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
