package com.hackatona.timevermelho.agente.contexto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.hackatona.timevermelho.motor.Caso;
import com.hackatona.timevermelho.motor.ClasseSwalis;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.StatusFila;
import com.hackatona.timevermelho.motor.texto.ExtratorDeTexto;
import com.hackatona.timevermelho.servico.AlertaService;
import com.hackatona.timevermelho.servico.FilaService;
import com.hackatona.timevermelho.servico.IndicadoresService;

/**
 * Lê os serviços que já existem, só pelos métodos de consulta. É a ÚNICA classe do agente que conhece os serviços
 * da aplicação (um teste de guarda confere isso). Os textos que vão para a IA são só agregados; as linhas de
 * pacientes de {@link #consultar} vão direto para a tela.
 */
@Component
public class LeituraAplicacaoSpring implements LeituraAplicacao {

    /** Até quantos pacientes encontrados a IA recebe o detalhe de cada um (sem identificação). */
    static final int DETALHE_ATE = 5;

    private final FilaService fila;
    private final IndicadoresService indicadores;
    private final AlertaService alertas;
    private final CsvOriginal csv;

    public LeituraAplicacaoSpring(FilaService fila, IndicadoresService indicadores, AlertaService alertas,
                                  CsvOriginal csv) {
        this.fila = fila;
        this.indicadores = indicadores;
        this.alertas = alertas;
        this.csv = csv;
    }

    @Override
    public ResumoFila resumoFila() {
        var snap = fila.atual();
        Map<String, Long> porClasse = new LinkedHashMap<>();
        for (ClasseSwalis c : ClasseSwalis.values()) porClasse.put(c.name(), 0L);
        Map<String, Long> porEspecialidade = new LinkedHashMap<>();
        for (ResultadoPaciente r : snap.ativos()) {
            porClasse.merge(r.classe().name(), 1L, Long::sum);
            String esp = r.getCaso().especialidade();
            if (esp != null) porEspecialidade.merge(esp, 1L, Long::sum);
        }
        return new ResumoFila(snap.ativos().size(), snap.todos().size() - snap.ativos().size(), porClasse,
                porEspecialidade);
    }

    @Override
    public List<Indicador> indicadores() {
        return indicadores.indicadores().comparacoes().stream()
                .map(c -> new Indicador(c.indicador(), c.antes(), c.depois())).toList();
    }

    @Override
    public List<String> especialidades() {
        return fila.atual().ativos().stream().map(r -> r.getCaso().especialidade()).filter(e -> e != null)
                .distinct().sorted().toList();
    }

    @Override
    public ResumoEspecialidade resumoEspecialidade(String especialidade) {
        var doGrupo = fila.atual().ativos().stream().filter(r -> especialidade.equals(r.getCaso().especialidade())).toList();
        if (doGrupo.isEmpty()) return null;
        Map<String, Long> porClasse = new LinkedHashMap<>();
        for (ClasseSwalis c : ClasseSwalis.values()) porClasse.put(c.name(), 0L);
        doGrupo.forEach(r -> porClasse.merge(r.classe().name(), 1L, Long::sum));
        return new ResumoEspecialidade(especialidade, doGrupo.size(), porClasse,
                (int) doGrupo.stream().filter(ResultadoPaciente::oncologico).count(),
                (int) doGrupo.stream().filter(r -> !r.getReavaliacoes().isEmpty()).count(),
                (int) doGrupo.stream().filter(r -> r.getStatus() == StatusFila.SAIDA_SUGERIDA).count());
    }

    @Override
    public Catalogo catalogo() {
        var todos = fila.atual().todos();
        List<String> procedimentos = todos.stream().map(r -> r.getCaso().procedimento()).filter(p -> p != null)
                .distinct().sorted().toList();
        List<String> especialidades = todos.stream().map(r -> r.getCaso().especialidade()).filter(e -> e != null)
                .distinct().sorted().toList();
        // o dataset tem o mesmo município escrito de jeitos diferentes ("PORTO ALEGRE", "porto alegre"):
        // vale a grafia mais comum de cada um
        Map<String, Map<String, Long>> grafias = new HashMap<>();
        for (ResultadoPaciente r : todos) {
            String m = r.getCaso().municipio();
            if (m == null || m.isBlank()) continue;
            grafias.computeIfAbsent(ExtratorDeTexto.normalizar(m).strip(), k -> new HashMap<>()).merge(m.strip(), 1L, Long::sum);
        }
        List<String> municipios = grafias.values().stream()
                .map(g -> g.entrySet().stream().max(Map.Entry.<String, Long>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder()))).orElseThrow().getKey())
                .sorted().toList();
        return new Catalogo(procedimentos, especialidades, municipios);
    }

    @Override
    public ResultadoConsulta consultar(ConsultaFila c) {
        var snap = fila.atual();
        List<ResultadoPaciente> base = c.status() == ConsultaFila.Status.ATIVA ? snap.ativos() : snap.todos();
        Set<String> ids = solicitacoesPedidas(c);
        List<ResultadoPaciente> achados = base.stream().filter(r -> combina(r, c, ids, false))
                .collect(Collectors.toCollection(ArrayList::new));
        // Nome digitado com erro ("Lopez" por "Lopes"): só se ninguém bateu exato, tenta os nomes parecidos
        boolean aproximado = false;
        if (achados.isEmpty() && !c.nomes().isEmpty()) {
            achados = base.stream().filter(r -> combina(r, c, ids, true)).collect(Collectors.toCollection(ArrayList::new));
            aproximado = !achados.isEmpty();
        }
        Comparator<ResultadoPaciente> ordem = switch (c.ordem()) {
            case ESPERA -> Comparator.comparingInt((ResultadoPaciente r) -> r.getCaso().diasEmFila()).reversed();
            case SCORE -> Comparator.comparingDouble(ResultadoPaciente::getScore).reversed();
            case POSICAO -> Comparator.comparing(ResultadoPaciente::getPosicao, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        achados.sort(ordem);
        List<LinhaPaciente> linhas = c.listar()
                ? achados.stream().limit(c.limite()).map(LeituraAplicacaoSpring::linha).toList()
                : List.of();
        return new ResultadoConsulta(achados.size(), resumir(c, achados, aproximado), linhas);
    }

    /**
     * As solicitações que ficaram na fila para os números e IDs pedidos, pelo CSV original: uma duplicada vira a
     * principal em que foi mesclada, e um ID de paciente vira todas as solicitações dele. {@code null} sem esse filtro.
     */
    private Set<String> solicitacoesPedidas(ConsultaFila c) {
        if (c.solicitacoes().isEmpty() && c.pacientes().isEmpty()) return null;
        Set<String> ids = new HashSet<>();
        for (String sol : c.solicitacoes()) {
            String principal = csv.principal(sol);
            ids.add(principal != null ? principal : sol);
        }
        for (String pac : c.pacientes()) {
            for (CsvOriginal.Registro reg : csv.doPaciente(pac)) {
                String principal = csv.principal(reg.solicitacaoId());
                ids.add(principal != null ? principal : reg.solicitacaoId());
            }
        }
        return ids;
    }

    private static boolean combina(ResultadoPaciente r, ConsultaFila c, Set<String> ids, boolean aproximado) {
        Caso caso = r.getCaso();
        if (c.status() == ConsultaFila.Status.SAIDA_SUGERIDA && r.getStatus() != StatusFila.SAIDA_SUGERIDA) return false;
        if (c.status() == ConsultaFila.Status.SAIDA_CONFIRMADA && r.getStatus() != StatusFila.SAIDA_CONFIRMADA) return false;
        if (!c.procedimentos().isEmpty() && !c.procedimentos().contains(caso.procedimento())) return false;
        if (!c.especialidades().isEmpty() && !c.especialidades().contains(caso.especialidade())) return false;
        if (!c.classes().isEmpty() && !c.classes().contains(r.classe().name())) return false;
        if (c.cidPrefixo() != null && (caso.cid() == null || !caso.cid().toUpperCase().replace(".", "")
                .startsWith(c.cidPrefixo().replace(".", "")))) return false;
        if (c.oncologico() != null && r.oncologico() != c.oncologico()) return false;
        if (!c.municipios().isEmpty() && (caso.municipio() == null || c.municipios().stream()
                .noneMatch(m -> ExtratorDeTexto.normalizar(m).strip().equals(ExtratorDeTexto.normalizar(caso.municipio()).strip())))) return false;
        if (c.sexo() != null && !c.sexo().equalsIgnoreCase(caso.sexo())) return false;
        if (c.idadeMin() != null && (caso.idade() == null || caso.idade() < c.idadeMin())) return false;
        if (c.idadeMax() != null && (caso.idade() == null || caso.idade() > c.idadeMax())) return false;
        if (c.acimaDoPrazo() != null && (r.percentualPrazo() > 100) != c.acimaDoPrazo()) return false;
        if (c.comReavaliacao() != null && r.getReavaliacoes().isEmpty() == c.comReavaliacao()) return false;
        // número de solicitação ou ID de paciente: vale a solicitação pedida (ou a principal dela) e, sem o CSV,
        // ainda o ID de paciente da própria fila
        if (ids != null && !ids.contains(caso.solicitacaoId()) && !c.pacientes().contains(caso.pacienteId())) return false;
        if (!c.nomes().isEmpty() && c.nomes().stream().noneMatch(n -> nomeContem(caso.nome(), n, aproximado))) return false;
        return true;
    }

    /** Palavras que a pessoa escreve junto do nome, mas que não fazem parte dele ("dona Eliane", "Ari da Costa"). */
    private static final Set<String> FORA_DO_NOME = Set.of("sr", "sra", "srta", "senhor", "senhora", "dona", "seu", "dr",
            "dra", "paciente", "da", "de", "do", "das", "dos", "e");

    /**
     * Cada palavra pedida bate com uma palavra do nome, sem acento e sem caixa: "eliane ramos" acha "Eliane Lopes Ramos".
     * Exato: a palavra do nome começa com a pedida. Aproximado (só usado quando ninguém bateu exato): também vale
     * um erro de digitação pequeno, de 1 letra em palavras de 4 a 6 letras e de até 2 a partir de 7.
     */
    static boolean nomeContem(String nome, String pedido, boolean aproximado) {
        if (nome == null || pedido == null) return false;
        List<String> palavras = List.of(ExtratorDeTexto.normalizar(nome).replaceAll("[^\\p{L} ]", " ").strip().split("\\s+"));
        List<String> pedidas = java.util.Arrays.stream(ExtratorDeTexto.normalizar(pedido).replaceAll("[^\\p{L} ]", " ")
                .strip().split("\\s+")).filter(p -> !p.isEmpty() && !FORA_DO_NOME.contains(p)).toList();
        if (pedidas.isEmpty()) return false;
        return pedidas.stream().allMatch(p -> palavras.stream().anyMatch(w -> w.startsWith(p)
                || (aproximado && parecida(w, p))));
    }

    private static boolean parecida(String palavraDoNome, String pedida) {
        if (pedida.length() < 4) return false;
        int tolerancia = pedida.length() >= 7 ? 2 : 1;
        return Math.abs(palavraDoNome.length() - pedida.length()) <= tolerancia
                && distancia(palavraDoNome, pedida) <= tolerancia;
    }

    /**
     * Distância de edição (Damerau-Levenshtein, alinhamento ótimo): quantas letras inserir, apagar, trocar ou inverter
     * com a vizinha ("elaine" → "eliane" é 1) para uma palavra virar a outra.
     */
    static int distancia(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int troca = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + troca);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    private static LinhaPaciente linha(ResultadoPaciente r) {
        Caso c = r.getCaso();
        return new LinhaPaciente(r.getPosicao(), c.solicitacaoId(), c.nome(), c.idade(), c.sexo(), c.municipio(),
                c.procedimento(), c.especialidade(), c.cidFormatado() != null ? c.cidFormatado() : c.cid(),
                c.cidDescricao(), r.oncologico(), r.classe().name(), Math.round(r.getScore() * 10) / 10.0,
                c.diasEmFila(), r.classe().prazoDias(), r.getStatus().name());
    }

    /** Os números da consulta, em texto, para a IA: nada de nome, identificador ou CID de alguém. */
    private String resumir(ConsultaFila c, List<ResultadoPaciente> achados, boolean aproximado) {
        StringBuilder sb = new StringBuilder();
        if (aproximado) {
            sb.append("Nenhum nome exatamente igual ao informado. A tabela mostra os nomes MAIS PARECIDOS (pequena diferença ")
                    .append("de grafia): diga isso e peça para a pessoa confirmar na tabela se é o paciente certo.\n");
        }
        sb.append("Filtros aplicados: ").append(ContextoDados.rotulo(c.descricaoParaIA())).append(".\n");
        if (!c.naoEncontrados().isEmpty()) {
            sb.append("Termos pedidos que NÃO existem na fila: ")
                    .append(c.naoEncontrados().stream().map(ContextoDados::rotulo).collect(Collectors.joining(", "))).append(".\n");
        }
        if (!c.solicitacoes().isEmpty()) {
            long faltam = c.solicitacoes().stream().filter(id -> csv.principal(id) == null)
                    .filter(id -> achados.stream().noneMatch(r -> id.equals(r.getCaso().solicitacaoId()))).count();
            if (faltam > 0) sb.append("Números de solicitação informados que NÃO existem no dataset: ").append(faltam).append(".\n");
            long duplicadas = c.solicitacoes().stream().filter(id -> csv.principal(id) != null && !id.equals(csv.principal(id))).count();
            if (duplicadas > 0) {
                sb.append("Números de solicitação informados que são DUPLICADAS no CSV original (mesmo paciente e mesmo ")
                        .append("procedimento), já mescladas na solicitação principal, que é a que aparece na tabela: ")
                        .append(duplicadas).append(".\n");
            }
        }
        if (!c.pacientes().isEmpty()) {
            long faltam = c.pacientes().stream().filter(p -> csv.doPaciente(p).isEmpty())
                    .filter(p -> achados.stream().noneMatch(r -> p.equals(r.getCaso().pacienteId()))).count();
            if (faltam > 0) sb.append("IDs de paciente informados que NÃO existem no dataset: ").append(faltam).append(".\n");
        }
        sb.append("Pacientes encontrados: ").append(achados.size()).append(".\n");
        if (achados.isEmpty()) return sb.toString().strip();
        if (c.porPaciente()) sb.append(pessoas(achados));
        if (achados.size() <= DETALHE_ATE) sb.append(detalhes(achados, csv));

        Map<String, Long> porClasse = new LinkedHashMap<>();
        for (ClasseSwalis cl : ClasseSwalis.values()) porClasse.put(cl.name() + " (prazo " + cl.prazoDias() + " d)", 0L);
        achados.forEach(r -> porClasse.merge(r.classe().name() + " (prazo " + r.classe().prazoDias() + " d)", 1L, Long::sum));
        sb.append("Por classe: ").append(pares(porClasse, 10)).append(".\n");
        sb.append("Por procedimento: ").append(pares(contar(achados, r -> r.getCaso().procedimento()), 8)).append(".\n");
        sb.append("Por especialidade: ").append(pares(contar(achados, r -> r.getCaso().especialidade()), 8)).append(".\n");
        long onco = achados.stream().filter(ResultadoPaciente::oncologico).count();
        sb.append("Oncológicos: ").append(onco).append(".\n");

        IntSummaryStatistics espera = achados.stream().mapToInt(r -> r.getCaso().diasEmFila()).summaryStatistics();
        sb.append("Dias em fila: média ").append(Math.round(espera.getAverage())).append(", menor ")
                .append(espera.getMin()).append(", maior ").append(espera.getMax()).append(".\n");
        sb.append("Acima do prazo da classe: ").append(achados.stream().filter(r -> r.percentualPrazo() > 100).count()).append(".\n");
        sb.append("Com motivo de reavaliação: ").append(achados.stream().filter(r -> !r.getReavaliacoes().isEmpty()).count())
                .append(". Com saída sugerida aguardando o regulador: ")
                .append(achados.stream().filter(r -> r.getStatus() == StatusFila.SAIDA_SUGERIDA).count()).append(".\n");

        IntSummaryStatistics idade = achados.stream().map(r -> r.getCaso().idade()).filter(i -> i != null)
                .mapToInt(Integer::intValue).summaryStatistics();
        if (idade.getCount() > 0) {
            sb.append("Idade: média ").append(Math.round(idade.getAverage())).append(", menor ").append(idade.getMin())
                    .append(", maior ").append(idade.getMax()).append(".\n");
        }
        sb.append("Sexo: ").append(pares(contar(achados, r -> r.getCaso().sexo()), 3)).append(".\n");
        sb.append("Municípios (os maiores): ").append(pares(contar(achados, r -> r.getCaso().municipio() == null ? null
                : r.getCaso().municipio().strip()), 6)).append(".\n");
        var posicoes = achados.stream().map(ResultadoPaciente::getPosicao).filter(p -> p != null).mapToInt(Integer::intValue)
                .summaryStatistics();
        if (posicoes.getCount() > 0) {
            sb.append("Posições na fila geral: da ").append(posicoes.getMin()).append("ª à ").append(posicoes.getMax()).append("ª.");
        }
        return sb.toString().strip();
    }

    /**
     * Para resultados pequenos (um paciente procurado pelo nome, por exemplo), os dados de cada um, na ordem da tabela,
     * SEM nome, número de solicitação nem CID: a IA consegue responder "qual a classe e a posição" sem identificar ninguém.
     */
    /**
     * Cada linha da tabela é uma solicitação, e uma pessoa pode ter mais de uma (procedimentos diferentes). Diz quantas
     * pessoas são de fato e, pelo CSV original, quantas solicitações elas têm lá e quantas eram duplicadas.
     */
    private String pessoas(List<ResultadoPaciente> achados) {
        List<String> pacientes = achados.stream().map(r -> r.getCaso().pacienteId()).filter(p -> p != null).distinct().toList();
        int noCsv = 0;
        int duplicadas = 0;
        for (String p : pacientes) {
            for (CsvOriginal.Registro reg : csv.doPaciente(p)) {
                noCsv++;
                if (!reg.solicitacaoId().equals(csv.principal(reg.solicitacaoId()))) duplicadas++;
            }
        }
        StringBuilder sb = new StringBuilder("Pessoas distintas entre os encontrados (pelo ID do paciente): ")
                .append(pacientes.size()).append(". Cada linha da tabela é uma solicitação; a mesma pessoa pode ter mais ")
                .append("de uma, para procedimentos diferentes.\n");
        if (noCsv > 0) {
            sb.append("No CSV original, essas pessoas têm ").append(noCsv).append(" solicitação(ões), das quais ")
                    .append(duplicadas).append(" duplicada(s) (mesmo paciente e mesmo procedimento), mesclada(s) na ")
                    .append("principal e fora da contagem da fila.\n");
        }
        return sb.toString();
    }

    private static String detalhes(List<ResultadoPaciente> achados, CsvOriginal csv) {
        StringBuilder sb = new StringBuilder("Detalhe de cada paciente encontrado, na ordem da tabela (sem identificação):\n");
        for (int i = 0; i < achados.size(); i++) {
            ResultadoPaciente r = achados.get(i);
            Caso c = r.getCaso();
            sb.append("- Paciente ").append(i + 1).append(" da tabela: classe ").append(r.classe().name()).append(" (prazo ")
                    .append(r.classe().prazoDias()).append(" d)")
                    .append(r.getPosicao() == null ? ", fora da fila ativa" : ", " + r.getPosicao() + "ª posição na fila geral")
                    .append(", status ").append(r.getStatus().name())
                    .append(", procedimento ").append(ContextoDados.rotulo(c.procedimento()))
                    .append(" (").append(ContextoDados.rotulo(c.especialidade())).append(")")
                    .append(c.idade() == null ? "" : ", " + c.idade() + " anos")
                    .append(r.oncologico() ? ", oncológico" : ", não oncológico")
                    .append(", ").append(c.diasEmFila()).append(" dias em fila (").append(r.percentualPrazo()).append("% do prazo)")
                    .append(", score ").append(Math.round(r.getScore() * 10) / 10.0)
                    .append(", motivos de reavaliação: ").append(r.getReavaliacoes().size())
                    .append(", solicitações duplicadas mescladas nesta: ").append(csv.duplicadasDe(c.solicitacaoId()))
                    .append(".\n");
        }
        return sb.toString();
    }

    private static Map<String, Long> contar(List<ResultadoPaciente> lista, Function<ResultadoPaciente, String> chave) {
        Map<String, Long> m = new HashMap<>();
        for (ResultadoPaciente r : lista) {
            String k = chave.apply(r);
            if (k != null && !k.isBlank()) m.merge(k, 1L, Long::sum);
        }
        return m;
    }

    private static String pares(Map<String, Long> mapa, int limite) {
        return mapa.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limite).map(e -> ContextoDados.rotulo(e.getKey()) + " " + e.getValue()).collect(Collectors.joining("; "));
    }

    @Override
    public List<GrupoReavaliacao> reavaliacoes() {
        // os itens de cada grupo (com nomes de pacientes) são descartados aqui: só título e total saem
        return alertas.alertas().reavaliacao().stream().map(g -> new GrupoReavaliacao(g.titulo(), g.total())).toList();
    }
}
