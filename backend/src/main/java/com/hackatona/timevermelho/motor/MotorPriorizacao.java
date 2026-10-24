package com.hackatona.timevermelho.motor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.hackatona.timevermelho.motor.texto.RegrasClinicas;

/**
 * Ponto de entrada do motor. Recebe todos os casos e devolve a fila ordenada e explicada.
 *
 * Ordem: classe SWALIS → score → % do prazo consumido → data de entrada. Depois a trava da regra de ouro.
 */
public class MotorPriorizacao {

    private final ExtratorDeSinais extrator;
    private final ClassificadorSwalis classificador = new ClassificadorSwalis();
    private final CalculadoraScore calculadora = new CalculadoraScore();
    private final GuardRegraDeOuro guard = new GuardRegraDeOuro();
    private final GeradorJustificativa justificativas = new GeradorJustificativa();
    private final AnalisadorReavaliacao reavaliacao = new AnalisadorReavaliacao();

    public static final Comparator<ResultadoPaciente> ORDEM_CLINICA = Comparator
            .comparing((ResultadoPaciente r) -> r.classe().ordinal())
            .thenComparing(ResultadoPaciente::getScore, Comparator.reverseOrder())
            .thenComparing(ResultadoPaciente::percentualPrazo, Comparator.reverseOrder())
            .thenComparing(r -> r.getCaso().dataEntrada())
            .thenComparing(r -> r.getCaso().solicitacaoId());

    /** Como a fila é ordenada hoje: prioridade manual e, depois, data de entrada. */
    public static final Comparator<ResultadoPaciente> ORDEM_MANUAL = Comparator
            .comparing((ResultadoPaciente r) -> rankManual(r.getCaso().prioridadeManual()))
            .thenComparing(r -> r.getCaso().dataEntrada())
            .thenComparing(r -> r.getCaso().solicitacaoId());

    public MotorPriorizacao(RegrasClinicas regras, LocalDate referencia) {
        this.extrator = new ExtratorDeSinais(regras, referencia);
    }

    public List<ResultadoPaciente> priorizar(List<Caso> casos, Pesos pesos, Map<String, DecisaoRegulador> decisoes) {
        List<ResultadoPaciente> todos = new ArrayList<>();
        for (Caso c : casos) {
            todos.add(avaliar(c, pesos, decisoes.getOrDefault(c.solicitacaoId(), DecisaoRegulador.NENHUMA)));
        }

        List<ResultadoPaciente> ativos = new ArrayList<>(todos.stream()
                .filter(r -> r.getStatus() == StatusFila.ATIVA).sorted(ORDEM_CLINICA).toList());
        ativos = guard.aplicar(ativos);
        for (int i = 0; i < ativos.size(); i++) ativos.get(i).setPosicao(i + 1);

        List<ResultadoPaciente> manual = ativos.stream().sorted(ORDEM_MANUAL).toList();
        for (int i = 0; i < manual.size(); i++) manual.get(i).setPosicaoManual(i + 1);

        List<ResultadoPaciente> fora = todos.stream().filter(r -> r.getStatus() != StatusFila.ATIVA)
                .sorted(ORDEM_CLINICA).toList();

        List<ResultadoPaciente> resultado = new ArrayList<>(ativos);
        resultado.addAll(fora);
        for (ResultadoPaciente r : resultado) {
            r.setJustificativa(justificativas.gerar(r));
            reavaliacao.analisar(r);
        }
        return resultado;
    }

    /** Avalia um paciente sozinho (sem posição). */
    public ResultadoPaciente avaliar(Caso c, Pesos pesos, DecisaoRegulador d) {
        List<Sinal> sinais = extrator.extrair(c);
        ResultadoPaciente r = new ResultadoPaciente(c, sinais);
        Classificacao cl = classificador.classificar(sinais);
        if (d.classe() != null) {
            ClasseSwalis nova = d.classe();
            // trava: oncológico nunca abaixo de A2, nem por ajuste manual
            if (r.oncologico() && ClasseSwalis.A2.maisUrgenteQue(nova)) nova = ClasseSwalis.A2;
            cl = cl.comAjuste(nova, d.justificativaClasse());
        }
        r.setClassificacao(cl);

        CalculadoraScore.Resultado score = calculadora.calcular(c, sinais, cl.classe(), pesos);
        r.setScore(score.total());
        r.setComponentes(score.componentes());
        r.setSemUrgencia(GuardRegraDeOuro.semUrgenciaClinica(c));

        Sinal saida = sinais.stream().filter(s -> s.tipo().categoria() == CategoriaSinal.SAIDA).findFirst().orElse(null);
        r.setSinalSaida(saida);
        if (Boolean.TRUE.equals(d.saidaConfirmada())) {
            r.setStatus(StatusFila.SAIDA_CONFIRMADA);
        } else if (saida != null && d.saidaConfirmada() == null && !r.oncologico()) {
            r.setStatus(StatusFila.SAIDA_SUGERIDA);
        }
        return r;
    }

    private static int rankManual(String p) {
        if (p == null) return 3;
        return switch (p) {
            case "alta" -> 0;
            case "media" -> 1;
            case "baixa" -> 2;
            default -> 3;
        };
    }
}
