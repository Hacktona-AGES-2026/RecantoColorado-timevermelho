package com.hackatona.timevermelho.motor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hackatona.timevermelho.motor.texto.RegrasClinicas;

/** Um caso por motivo de reavaliação listado em {@link TipoReavaliacao}. */
class AnalisadorReavaliacaoTest {

    static final LocalDate REF = LocalDate.of(2026, 10, 23);
    final MotorPriorizacao motor = new MotorPriorizacao(RegrasClinicas.carregarPadrao(), REF);
    final AnalisadorReavaliacao analisador = new AnalisadorReavaliacao();

    static Caso.CasoBuilder caso(String cid) {
        return Caso.builder().solicitacaoId("S1").pacienteId("P1").nome("Paciente").cid(cid).cidFormatado(cid)
                .procedimento("Proc").riscoCirurgico(2).asa(2).dor(1).limitacao("nenhuma")
                .justificativa("Paciente com indicacao cirurgica eletiva, estavel.")
                .dataEntrada(REF.minusDays(100)).diasEmFila(100).prioridadeManual("media")
                .flagOncologico(cid.startsWith("C") ? "sim" : "nao").flagPiora("nao").eventos(List.of());
    }

    static Evento evento(String id, String tipo, String resultado, Double valor, int diasAtras) {
        return new Evento(id, "S1", REF.minusDays(diasAtras), tipo, resultado, valor, "descricao", "origem", false);
    }

    private ResultadoPaciente analisar(Caso c) {
        return analisar(c, DecisaoRegulador.NENHUMA);
    }

    private ResultadoPaciente analisar(Caso c, DecisaoRegulador d) {
        ResultadoPaciente r = motor.avaliar(c, Pesos.PADRAO, d);
        analisador.analisar(r);
        return r;
    }

    private static List<TipoReavaliacao> tipos(ResultadoPaciente r) {
        return r.getReavaliacoes().stream().map(Reavaliacao::tipo).toList();
    }

    @Test
    void casoSemProblemaNaoGeraReavaliacao() {
        assertThat(tipos(analisar(caso("K409").build()))).isEmpty();
    }

    // ---------- prioridade divergente ----------

    @Test
    void urgenteSemPrioridadeAltaNoCadastroEDivergente() {
        var r = analisar(caso("C504").prioridadeManual("baixa").build());
        assertThat(tipos(r)).contains(TipoReavaliacao.PRIORIDADE_DIVERGENTE);
        assertThat(r.getReavaliacoes().getFirst().mensagem()).contains("baixa").contains("A2");
    }

    @Test
    void urgenteComPrioridadeAltaNaoDiverge() {
        assertThat(tipos(analisar(caso("C504").prioridadeManual("alta").build())))
                .doesNotContain(TipoReavaliacao.PRIORIDADE_DIVERGENTE);
    }

    @Test
    void classeDComPrioridadeAltaPedeConfirmacaoDoMotivo() {
        var r = analisar(caso("K409").prioridadeManual("alta").build());
        assertThat(r.classe()).isEqualTo(ClasseSwalis.D);
        assertThat(tipos(r)).contains(TipoReavaliacao.PRIORIDADE_DIVERGENTE);
    }

    @Test
    void semPrioridadeNoCadastroApareceComoSemPrioridadeNaMensagem() {
        var r = analisar(caso("C504").prioridadeManual(null).build());
        assertThat(r.getReavaliacoes().getFirst().mensagem()).contains("sem prioridade");
    }

    // ---------- esquecido ----------

    @Test
    void maisQueODobroDoPrazoSemRevisaoEEsquecido() {
        // classe D = 360 dias; 800 > 720
        var r = analisar(caso("K409").diasEmFila(800).dataUltimaRevisao(null).build());
        assertThat(r.classe()).isEqualTo(ClasseSwalis.D);
        assertThat(tipos(r)).contains(TipoReavaliacao.ESQUECIDO);
    }

    @Test
    void revisaoIgualAEntradaContaComoNuncaRevisada() {
        LocalDate entrada = REF.minusDays(800);
        var r = analisar(caso("K409").diasEmFila(800).dataEntrada(entrada).dataUltimaRevisao(entrada).build());
        assertThat(tipos(r)).contains(TipoReavaliacao.ESQUECIDO);
    }

    @Test
    void esperaLongaMasComRevisaoPosteriorNaoEEsquecido() {
        var r = analisar(caso("K409").diasEmFila(800).dataEntrada(REF.minusDays(800))
                .dataUltimaRevisao(REF.minusDays(30)).build());
        assertThat(tipos(r)).doesNotContain(TipoReavaliacao.ESQUECIDO);
    }

    @Test
    void esperaDentroDoDobroDoPrazoNaoEEsquecido() {
        var r = analisar(caso("K409").diasEmFila(700).dataUltimaRevisao(null).build());
        assertThat(tipos(r)).doesNotContain(TipoReavaliacao.ESQUECIDO);
    }

    // ---------- qualidade do dado ----------

    @Test
    void flagOncologicoQueNaoBateComOCidEFlagDivergente() {
        var r = analisar(caso("C504").flagOncologico("nao").prioridadeManual("alta").build());
        assertThat(tipos(r)).contains(TipoReavaliacao.FLAG_DIVERGENTE);
    }

    @Test
    void flagDePioraDesatualizadaEFlagDivergente() {
        var c = caso("K409").flagPiora("nao").eventos(List.of(evento("E1", "pronto_socorro", "observacao", null, 20))).build();
        assertThat(tipos(analisar(c))).contains(TipoReavaliacao.FLAG_DIVERGENTE);
    }

    @Test
    void dorOuLimitacaoFaltandoEDadoIncompleto() {
        assertThat(tipos(analisar(caso("K409").dor(null).build()))).contains(TipoReavaliacao.DADO_INCOMPLETO);
        assertThat(tipos(analisar(caso("K409").limitacao(null).build()))).contains(TipoReavaliacao.DADO_INCOMPLETO);
    }

    @Test
    void duasLigacoesSeguidasSemRespostaEhSemContato() {
        var c = caso("K409").eventos(List.of(
                evento("E1", "contato_telefonico", "nao_atendeu", null, 60),
                evento("E2", "contato_telefonico", "nao_atendeu", null, 10))).build();
        assertThat(tipos(analisar(c))).contains(TipoReavaliacao.SEM_CONTATO);
    }

    @Test
    void umaLigacaoSemRespostaOuUmaAtendidaDepoisNaoEhSemContato() {
        var uma = caso("K409").eventos(List.of(evento("E1", "contato_telefonico", "nao_atendeu", null, 10))).build();
        var atendeu = caso("K409").eventos(List.of(
                evento("E1", "contato_telefonico", "nao_atendeu", null, 60),
                evento("E2", "contato_telefonico", "atendeu", null, 10))).build();
        assertThat(tipos(analisar(uma))).doesNotContain(TipoReavaliacao.SEM_CONTATO);
        assertThat(tipos(analisar(atendeu))).doesNotContain(TipoReavaliacao.SEM_CONTATO);
    }

    @Test
    void hba1cAcimaDoLimiteEDescompensacaoClinica() {
        var c = caso("K409").eventos(List.of(evento("E1", "hba1c", "resultado", 9.8, 15))).build();
        var r = analisar(c);
        assertThat(tipos(r)).contains(TipoReavaliacao.DESCOMPENSACAO_CLINICA);
        assertThat(r.getReavaliacoes().stream().filter(x -> x.tipo() == TipoReavaliacao.DESCOMPENSACAO_CLINICA).findFirst()
                .orElseThrow().mensagem()).contains("diabetes descontrolado");
    }

    @Test
    void hba1cNormalNaoGeraDescompensacao() {
        var c = caso("K409").eventos(List.of(evento("E1", "hba1c", "resultado", 6.5, 15))).build();
        assertThat(tipos(analisar(c))).doesNotContain(TipoReavaliacao.DESCOMPENSACAO_CLINICA);
    }

    // ---------- saída ----------

    private Caso desistente(String cid) {
        return caso(cid).eventos(List.of(evento("E1", "contato_telefonico", "desistiu", null, 5))).build();
    }

    @Test
    void desistenciaDeNaoOncologicoPedeConfirmacaoDaSaida() {
        var r = analisar(desistente("K409"));
        assertThat(r.getStatus()).isEqualTo(StatusFila.SAIDA_SUGERIDA);
        assertThat(tipos(r)).contains(TipoReavaliacao.CONFIRMAR_SAIDA);
    }

    @Test
    void saidaRecusadaPeloReguladorNaoPedeConfirmacaoDeNovo() {
        var r = analisar(desistente("K409"), new DecisaoRegulador(null, null, false, "paciente quer operar"));
        assertThat(r.getStatus()).isEqualTo(StatusFila.ATIVA);
        assertThat(tipos(r)).doesNotContain(TipoReavaliacao.CONFIRMAR_SAIDA);
    }

    @Test
    void saidaConfirmadaNaoGeraNovaPendencia() {
        var r = analisar(desistente("K409"), new DecisaoRegulador(null, null, true, "confirmado"));
        assertThat(r.getStatus()).isEqualTo(StatusFila.SAIDA_CONFIRMADA);
        assertThat(tipos(r)).doesNotContain(TipoReavaliacao.CONFIRMAR_SAIDA);
    }

    @Test
    void oncologicoQueDesisteContinuaNaFilaAteOReguladorConfirmar() {
        var r = analisar(desistente("C504"));
        assertThat(r.getStatus()).isEqualTo(StatusFila.ATIVA);
        var saida = r.getReavaliacoes().stream().filter(x -> x.tipo() == TipoReavaliacao.CONFIRMAR_SAIDA).findFirst()
                .orElseThrow();
        assertThat(saida.mensagem()).contains("oncológico").contains("mantido na fila");
    }

    // ---------- regra de ouro ----------

    @Test
    void pacienteMovidoPelaRegraDeOuroGeraAviso() {
        ResultadoPaciente r = motor.avaliar(caso("C504").build(), Pesos.PADRAO, DecisaoRegulador.NENHUMA);
        r.setMovidoPelaRegraDeOuro(true);
        analisador.analisar(r);
        assertThat(tipos(r)).contains(TipoReavaliacao.REGRA_DE_OURO);
    }
}
