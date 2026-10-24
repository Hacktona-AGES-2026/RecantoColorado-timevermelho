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
 * Ordem: classe SWALIS → score → % do prazo consumido → data de entrada. Depois a trava da regra de ouro,
 * como restrição explícita, sobre a referência pedida (ATUAL para a fila operacional, OFICIAL para a entrega).
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

    /**
     * O que conta como evidência de agravamento para uma promoção manual passar à frente de protegidos:
     * só dados ESTRUTURADOS — atendimento ou exame de piora registrado, ou CID de urgência. Texto livre (do médico,
     * das observações ou de eventos), dor, limitação, dado faltando e relato de piora não contam sozinhos.
     */
    static final java.util.Set<TipoSinal> EVIDENCIA_DE_AGRAVAMENTO = java.util.EnumSet.of(
            TipoSinal.PROGRESSAO_IMAGEM, TipoSinal.PS_RECENTE, TipoSinal.INTERNACAO_RECENTE, TipoSinal.REAVALIACAO_PIORA,
            TipoSinal.CID_URGENTE);

    /** Sinais lidos do texto livre do cadastro (justificativa do médico e observações). */
    private static final java.util.Set<TipoSinal> TIPOS_DE_TEXTO = java.util.EnumSet.of(TipoSinal.ALARME_GRAVE,
            TipoSinal.SUSPEITA_MALIGNIDADE, TipoSinal.ALARME_FUNCIONAL, TipoSinal.GRAVIDADE_CLINICA, TipoSinal.PS_TEXTO,
            TipoSinal.RELATA_PIORA, TipoSinal.ALARME_A_CONFIRMAR);

    public static boolean vemDeTextoLivre(Sinal s) {
        return TIPOS_DE_TEXTO.contains(s.tipo())
                && ("justificativa_clinica".equals(s.fonte()) || "observacoes".equals(s.fonte()));
    }

    public MotorPriorizacao(RegrasClinicas regras, LocalDate referencia) {
        this.extrator = new ExtratorDeSinais(regras, referencia);
    }

    /** Fila operacional (situação clínica atual). */
    public List<ResultadoPaciente> priorizar(List<Caso> casos, Pesos pesos, Map<String, DecisaoRegulador> decisoes) {
        return priorizar(casos, pesos, decisoes, Referencia.ATUAL);
    }

    public List<ResultadoPaciente> priorizar(List<Caso> casos, Pesos pesos, Map<String, DecisaoRegulador> decisoes,
                                             Referencia referencia) {
        List<ResultadoPaciente> todos = new ArrayList<>();
        for (Caso c : casos) {
            todos.add(avaliar(c, pesos, decisoes.getOrDefault(c.solicitacaoId(), DecisaoRegulador.NENHUMA), referencia));
        }

        List<ResultadoPaciente> ativos = new ArrayList<>(todos.stream()
                .filter(r -> r.getStatus().naFila()).sorted(ORDEM_CLINICA).toList());
        ativos = aplicarPosicoesFixadas(ativos);
        ativos = guard.aplicar(ativos);
        for (int i = 0; i < ativos.size(); i++) ativos.get(i).setPosicao(i + 1);

        List<ResultadoPaciente> manual = ativos.stream().sorted(ORDEM_MANUAL).toList();
        for (int i = 0; i < manual.size(); i++) manual.get(i).setPosicaoManual(i + 1);

        List<ResultadoPaciente> fora = todos.stream().filter(r -> !r.getStatus().naFila())
                .sorted(ORDEM_CLINICA).toList();

        List<ResultadoPaciente> resultado = new ArrayList<>(ativos);
        resultado.addAll(fora);
        for (ResultadoPaciente r : resultado) {
            r.setJustificativa(justificativas.gerar(r));
            reavaliacao.analisar(r);
        }
        return resultado;
    }

    /**
     * Posição escolhida por um médico ou pelo regulador: o paciente sobe até ela; os outros só abrem espaço e mantêm a
     * ordem entre si. Se a posição calculada já for melhor, ela fica. Depois disso a trava da regra de ouro ainda roda:
     * sem urgência registrada, o paciente não passa à frente de quem tem câncer.
     */
    static List<ResultadoPaciente> aplicarPosicoesFixadas(List<ResultadoPaciente> fila) {
        List<ResultadoPaciente> fixados = new ArrayList<>();
        java.util.Map<ResultadoPaciente, Integer> natural = new java.util.IdentityHashMap<>();
        for (int i = 0; i < fila.size(); i++) {
            ResultadoPaciente r = fila.get(i);
            if (r.getPosicaoFixada() != null) {
                fixados.add(r);
                natural.put(r, i);
            }
        }
        if (fixados.isEmpty()) return fila;
        fixados.sort(Comparator.comparing(ResultadoPaciente::getPosicaoFixada).thenComparing(r -> r.getCaso().solicitacaoId()));
        List<ResultadoPaciente> nova = new ArrayList<>(fila);
        nova.removeAll(fixados);
        for (ResultadoPaciente r : fixados) {
            int alvo = Math.min(Math.max(r.getPosicaoFixada(), 1) - 1, natural.get(r));
            if (alvo < natural.get(r) && !r.isProtegido() && !r.isPromocaoSemEvidencia()) {
                // subiu por decisão manual: sem evidência registrada, a trava não deixa passar quem tem câncer
                Sinal evidencia = r.getSinais().stream().filter(s -> EVIDENCIA_DE_AGRAVAMENTO.contains(s.tipo()))
                        .findFirst().orElse(null);
                if (r.getEvidenciaDaPromocao() == null) r.setEvidenciaDaPromocao(evidencia);
                r.setPromocaoSemEvidencia(evidencia == null);
            }
            nova.add(Math.min(alvo, nova.size()), r);
        }
        return nova;
    }

    /** Avalia um paciente sozinho (sem posição), na fila operacional. */
    public ResultadoPaciente avaliar(Caso c, Pesos pesos, DecisaoRegulador d) {
        return avaliar(c, pesos, d, Referencia.ATUAL);
    }

    public ResultadoPaciente avaliar(Caso c, Pesos pesos, DecisaoRegulador d, Referencia referencia) {
        List<Sinal> sinais = extrator.extrair(c);
        ResultadoPaciente r = new ResultadoPaciente(c, sinais);
        Classificacao cl = classificador.classificar(sinais);
        if (d.classe() != null) {
            ClasseSwalis nova = d.classe();
            // trava: oncológico nunca abaixo de A2, nem por ajuste manual
            if (r.oncologico() && ClasseSwalis.A2.maisUrgenteQue(nova)) nova = ClasseSwalis.A2;
            cl = cl.comAjuste(nova, d.justificativaClasse());
        }
        // piso da classe vigente (rebaixamento pendente, rejeitado ou bloqueado): a classe nunca cai abaixo dele
        if (d.classeMinima() != null && d.classeMinima().maisUrgenteQue(cl.classe())) {
            cl = cl.comAjuste(d.classeMinima(), d.justificativaMinima());
        }
        r.setClassificacao(cl);
        // quanto da classe vem só do texto livre: a posição é mantida, mas a classe fica provisória (revisão humana)
        ClasseSwalis estruturada = classificador.classificar(sinais.stream().filter(s -> !vemDeTextoLivre(s)).toList()).classe();
        r.setClasseEstruturada(estruturada);
        r.setClassePorTexto(cl.classeCalculada().maisUrgenteQue(estruturada));

        CalculadoraScore.Resultado score = calculadora.calcular(c, sinais, cl.classe(), pesos);
        r.setScore(score.total());
        r.setComponentes(score.componentes());
        // regra de ouro: quem é "sem urgência" e quem é protegido dependem da referência
        r.setReferencia(referencia);
        if (referencia == Referencia.OFICIAL) {
            r.setSemUrgencia(GuardRegraDeOuro.semUrgenciaOficial(c));
            r.setProtegido(c.oncologicoPeloCid());
        } else {
            r.setSemUrgencia(GuardRegraDeOuro.semUrgenciaAtual(c, cl.classeCalculada()));
            r.setProtegido(r.oncologico());
            boolean urgenteParaOFilaViva = cl.classeCalculada() == ClasseSwalis.A1 || cl.classeCalculada() == ClasseSwalis.A2;
            r.setDivergenteDoVerificador(urgenteParaOFilaViva && !r.isProtegido()
                    && GuardRegraDeOuro.verificadorNaoVeUrgencia(c));
            r.setPosicaoFixada(d.posicaoFixada());
            r.setJustificativaPosicao(d.justificativaPosicao());
            // promoção manual acima da classe calculada: só passa protegidos com evidência de agravamento registrada
            // (a posição fixada recebe a mesma checagem em aplicarPosicoesFixadas, só quando de fato faz o paciente subir)
            boolean promovido = cl.ajustada() && cl.classe().maisUrgenteQue(cl.classeCalculada());
            if (promovido && !r.isProtegido()) {
                Sinal evidencia = sinais.stream().filter(s -> EVIDENCIA_DE_AGRAVAMENTO.contains(s.tipo())).findFirst().orElse(null);
                r.setEvidenciaDaPromocao(evidencia);
                r.setPromocaoSemEvidencia(evidencia == null);
            }
        }

        Sinal saida = sinais.stream().filter(s -> s.tipo().categoria() == CategoriaSinal.SAIDA).findFirst().orElse(null);
        r.setSinalSaida(saida);
        // Só o regulador tira alguém da fila. A sugestão mantém o paciente na posição calculada.
        if (Boolean.TRUE.equals(d.saidaConfirmada())) {
            r.setStatus(StatusFila.SAIDA_CONFIRMADA);
        } else if (saida != null && d.saidaConfirmada() == null) {
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
