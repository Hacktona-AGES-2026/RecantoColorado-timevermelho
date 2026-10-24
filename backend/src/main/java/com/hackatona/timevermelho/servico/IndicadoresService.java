package com.hackatona.timevermelho.servico;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.importacao.RelatorioImportacao;
import com.hackatona.timevermelho.motor.ClasseSwalis;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.StatusFila;
import com.hackatona.timevermelho.motor.TipoSinal;

/**
 * Compara a ordem de hoje (prioridade manual + data de entrada) com a ordem do FilaVIVA.
 * "Antes" usa posicaoManual e "depois" usa posicao, sempre sobre os mesmos pacientes ativos.
 */
@Service
public class IndicadoresService {

    private final FilaService fila;
    private final RelatorioImportacao relatorio;

    public IndicadoresService(FilaService fila, RelatorioImportacao relatorio) {
        this.fila = fila;
        this.relatorio = relatorio;
    }

    public Dtos.Indicadores indicadores() {
        var snap = fila.atual();
        List<ResultadoPaciente> ativos = snap.ativos();
        int n = ativos.size();
        Function<ResultadoPaciente, Integer> antes = ResultadoPaciente::getPosicaoManual;
        Function<ResultadoPaciente, Integer> depois = ResultadoPaciente::getPosicao;

        Predicate<ResultadoPaciente> onco = r -> r.getCaso().oncologicoPeloCid();
        Predicate<ResultadoPaciente> calmo = ResultadoPaciente::isSemUrgencia;
        Predicate<ResultadoPaciente> psRecente = r -> r.temSinal(TipoSinal.PS_RECENTE) || r.temSinal(TipoSinal.INTERNACAO_RECENTE);
        Predicate<ResultadoPaciente> bxCidBenigno = r -> r.temSinal(TipoSinal.BIOPSIA_POSITIVA) && !r.getCaso().oncologicoPeloCid();
        Predicate<ResultadoPaciente> urgente = r -> r.classe() == ClasseSwalis.A1 || r.classe() == ClasseSwalis.A2;

        List<Dtos.Comparacao> comp = new ArrayList<>();
        comp.add(new Dtos.Comparacao("Oncológicos no primeiro terço da fila",
                pct(ativos, onco, antes, p -> p <= n / 3.0), pct(ativos, onco, depois, p -> p <= n / 3.0),
                "Pacientes com CID C* entre os primeiros " + (n / 3) + " da fila"));
        comp.add(new Dtos.Comparacao("Posição mediana dos oncológicos",
                mediana(ativos, onco, antes), mediana(ativos, onco, depois), "Quanto menor, mais cedo operam"));
        comp.add(new Dtos.Comparacao("Oncológicos abaixo de um paciente sem urgência",
                String.valueOf(violacoes(ativos, antes)), String.valueOf(violacoes(ativos, depois)),
                "Regra de ouro: precisa ser zero"));
        comp.add(new Dtos.Comparacao("Biópsia positiva com CID benigno: posição mediana",
                mediana(ativos, bxCidBenigno, antes), mediana(ativos, bxCidBenigno, depois),
                ativos.stream().filter(bxCidBenigno).count() + " pacientes que o cadastro ainda não trata como câncer"));
        comp.add(new Dtos.Comparacao("PS ou internação nos últimos 180 dias na primeira metade",
                pct(ativos, psRecente, antes, p -> p <= n / 2.0), pct(ativos, psRecente, depois, p -> p <= n / 2.0),
                "Quem piorou durante a espera"));
        comp.add(new Dtos.Comparacao("Sem urgência clínica no último terço",
                pct(ativos, calmo, antes, p -> p > 2 * n / 3.0), pct(ativos, calmo, depois, p -> p > 2 * n / 3.0),
                "Definição conservadora do verificador oficial"));
        comp.add(new Dtos.Comparacao("Urgentes (A1/A2) sem prioridade \"alta\" no cadastro",
                String.valueOf(ativos.stream().filter(urgente).filter(r -> !"alta".equals(r.getCaso().prioridadeManual())).count()),
                "0", "Casos urgentes que a prioridade manual não enxergava"));

        // décimos da fila
        List<Dtos.Faixa> faixas = new ArrayList<>();
        for (int d = 0; d < 10; d++) {
            int ini = d * n / 10 + 1;
            int fim = (d + 1) * n / 10;
            faixas.add(new Dtos.Faixa((d + 1) + "º décimo",
                    contar(ativos, onco, antes, ini, fim), contar(ativos, onco, depois, ini, fim),
                    contar(ativos, calmo, antes, ini, fim), contar(ativos, calmo, depois, ini, fim)));
        }

        Map<String, Long> porClasse = new LinkedHashMap<>();
        for (ClasseSwalis c : ClasseSwalis.values()) porClasse.put(c.name(), 0L);
        ativos.forEach(r -> porClasse.merge(r.classe().name(), 1L, Long::sum));

        List<Dtos.LinhaMatriz> matriz = new ArrayList<>();
        for (String p : List.of("alta", "media", "baixa", "sem prioridade")) {
            Map<String, Long> linha = new LinkedHashMap<>();
            for (ClasseSwalis c : ClasseSwalis.values()) linha.put(c.name(), 0L);
            ativos.stream().filter(r -> p.equals(r.getCaso().prioridadeManual() == null ? "sem prioridade" : r.getCaso().prioridadeManual()))
                    .forEach(r -> linha.merge(r.classe().name(), 1L, Long::sum));
            matriz.add(new Dtos.LinhaMatriz(p, linha));
        }

        List<Dtos.GrupoPosicao> grupos = new ArrayList<>();
        grupo(grupos, ativos, "Distância até 30 km", r -> dist(r) != null && dist(r) <= 30);
        grupo(grupos, ativos, "Distância 31 a 100 km", r -> dist(r) != null && dist(r) > 30 && dist(r) <= 100);
        grupo(grupos, ativos, "Distância 101 a 300 km", r -> dist(r) != null && dist(r) > 100 && dist(r) <= 300);
        grupo(grupos, ativos, "Distância acima de 300 km", r -> dist(r) != null && dist(r) > 300);
        grupo(grupos, ativos, "Idade abaixo de 60", r -> idade(r) < 60);
        grupo(grupos, ativos, "Idade 60 a 74", r -> idade(r) >= 60 && idade(r) < 75);
        grupo(grupos, ativos, "Idade 75 ou mais", r -> idade(r) >= 75);

        List<Dtos.QualidadeDado> qualidade = new ArrayList<>();
        relatorio.itens().forEach((k, v) -> qualidade.add(new Dtos.QualidadeDado(k, v)));
        qualidade.add(new Dtos.QualidadeDado("Biópsia positiva com CID benigno no cadastro",
                snap.todos().stream().filter(bxCidBenigno).count()));
        qualidade.add(new Dtos.QualidadeDado("Flag de piora desatualizada",
                snap.todos().stream().filter(r -> r.temSinal(TipoSinal.FLAG_PIORA_DESATUALIZADA)).count()));
        qualidade.add(new Dtos.QualidadeDado("Saídas sugeridas aguardando o regulador",
                snap.todos().stream().filter(r -> r.getStatus() == StatusFila.SAIDA_SUGERIDA).count()));

        return new Dtos.Indicadores(n, snap.todos().size() - n, porClasse, comp, faixas, matriz, grupos, qualidade);
    }

    private static Integer dist(ResultadoPaciente r) {
        return r.getCaso().distanciaKm();
    }

    private static int idade(ResultadoPaciente r) {
        return r.getCaso().idade() == null ? 0 : r.getCaso().idade();
    }

    private static void grupo(List<Dtos.GrupoPosicao> grupos, List<ResultadoPaciente> ativos, String nome,
                              Predicate<ResultadoPaciente> filtro) {
        var g = ativos.stream().filter(filtro).toList();
        if (g.isEmpty()) return;
        double a = g.stream().mapToInt(ResultadoPaciente::getPosicaoManual).average().orElse(0);
        double d = g.stream().mapToInt(ResultadoPaciente::getPosicao).average().orElse(0);
        grupos.add(new Dtos.GrupoPosicao(nome, g.size(), Math.round(a), Math.round(d)));
    }

    private static int contar(List<ResultadoPaciente> ativos, Predicate<ResultadoPaciente> filtro,
                              Function<ResultadoPaciente, Integer> pos, int ini, int fim) {
        return (int) ativos.stream().filter(filtro).map(pos).filter(p -> p >= ini && p <= fim).count();
    }

    private static String pct(List<ResultadoPaciente> ativos, Predicate<ResultadoPaciente> filtro,
                              Function<ResultadoPaciente, Integer> pos, Predicate<Integer> cond) {
        var g = ativos.stream().filter(filtro).map(pos).toList();
        if (g.isEmpty()) return "—";
        long ok = g.stream().filter(cond).count();
        return String.format(Locale.ROOT, "%d%%", Math.round(100.0 * ok / g.size()));
    }

    private static String mediana(List<ResultadoPaciente> ativos, Predicate<ResultadoPaciente> filtro,
                                  Function<ResultadoPaciente, Integer> pos) {
        var g = ativos.stream().filter(filtro).map(pos).sorted().toList();
        if (g.isEmpty()) return "—";
        return String.valueOf(g.get(g.size() / 2));
    }

    /** Quantos oncológicos ficam abaixo do primeiro paciente sem urgência, numa dada ordem. */
    private static long violacoes(List<ResultadoPaciente> ativos, Function<ResultadoPaciente, Integer> pos) {
        int primeiroCalmo = ativos.stream().filter(ResultadoPaciente::isSemUrgencia).map(pos)
                .min(Integer::compare).orElse(Integer.MAX_VALUE);
        return ativos.stream().filter(r -> r.getCaso().oncologicoPeloCid()).map(pos).filter(p -> p > primeiroCalmo).count();
    }
}
