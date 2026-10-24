package com.hackatona.timevermelho.agente.contexto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final FilaService fila;
    private final IndicadoresService indicadores;
    private final AlertaService alertas;

    public LeituraAplicacaoSpring(FilaService fila, IndicadoresService indicadores, AlertaService alertas) {
        this.fila = fila;
        this.indicadores = indicadores;
        this.alertas = alertas;
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
        List<ResultadoPaciente> achados = base.stream().filter(r -> combina(r, c)).collect(Collectors.toCollection(ArrayList::new));
        Comparator<ResultadoPaciente> ordem = switch (c.ordem()) {
            case ESPERA -> Comparator.comparingInt((ResultadoPaciente r) -> r.getCaso().diasEmFila()).reversed();
            case SCORE -> Comparator.comparingDouble(ResultadoPaciente::getScore).reversed();
            case POSICAO -> Comparator.comparing(ResultadoPaciente::getPosicao, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        achados.sort(ordem);
        List<LinhaPaciente> linhas = c.listar()
                ? achados.stream().limit(c.limite()).map(LeituraAplicacaoSpring::linha).toList()
                : List.of();
        return new ResultadoConsulta(achados.size(), resumir(c, achados), linhas);
    }

    private static boolean combina(ResultadoPaciente r, ConsultaFila c) {
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
        return true;
    }

    private static LinhaPaciente linha(ResultadoPaciente r) {
        Caso c = r.getCaso();
        return new LinhaPaciente(r.getPosicao(), c.solicitacaoId(), c.nome(), c.idade(), c.sexo(), c.municipio(),
                c.procedimento(), c.especialidade(), c.cidFormatado() != null ? c.cidFormatado() : c.cid(),
                c.cidDescricao(), r.oncologico(), r.classe().name(), Math.round(r.getScore() * 10) / 10.0,
                c.diasEmFila(), r.classe().prazoDias(), r.getStatus().name());
    }

    /** Os números da consulta, em texto, para a IA: nada de nome, identificador ou CID de alguém. */
    private static String resumir(ConsultaFila c, List<ResultadoPaciente> achados) {
        StringBuilder sb = new StringBuilder();
        sb.append("Filtros aplicados: ").append(ContextoDados.rotulo(c.descricao())).append(".\n");
        if (!c.naoEncontrados().isEmpty()) {
            sb.append("Termos pedidos que NÃO existem na fila: ")
                    .append(c.naoEncontrados().stream().map(ContextoDados::rotulo).collect(Collectors.joining(", "))).append(".\n");
        }
        sb.append("Pacientes encontrados: ").append(achados.size()).append(".\n");
        if (achados.isEmpty()) return sb.toString().strip();

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
