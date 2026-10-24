package com.hackatona.timevermelho.motor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.hackatona.timevermelho.motor.texto.RegrasClinicas;

class MotorPriorizacaoTest {

    static final LocalDate REF = LocalDate.of(2026, 10, 23);
    final MotorPriorizacao motor = new MotorPriorizacao(RegrasClinicas.carregarPadrao(), REF);

    static Caso.CasoBuilder caso(String id, String cid) {
        return Caso.builder().solicitacaoId(id).pacienteId("PAC-" + id).nome("Paciente " + id)
                .cid(cid).cidFormatado(cid).procedimento("Proc").riscoCirurgico(2).asa(2)
                .dor(1).limitacao("nenhuma").justificativa("Paciente com indicacao cirurgica eletiva, estavel.")
                .dataEntrada(REF.minusDays(100)).diasEmFila(100).prioridadeManual("media")
                .flagOncologico(cid.startsWith("C") ? "sim" : "nao").flagPiora("nao").eventos(List.of());
    }

    static Evento evento(String sol, String tipo, String resultado, int diasAtras) {
        return new Evento("EVT-" + sol + tipo, sol, REF.minusDays(diasAtras), tipo, resultado, null, "laudo", "lab", false);
    }

    ResultadoPaciente avaliar(Caso c) {
        return motor.avaliar(c, Pesos.PADRAO, DecisaoRegulador.NENHUMA);
    }

    @Test
    void oncologicoComPrioridadeBaixaFicaNoMinimoA2() {
        var r = avaliar(caso("1", "C504").prioridadeManual("baixa").build());
        assertThat(r.classe()).isEqualTo(ClasseSwalis.A2);
    }

    @Test
    void biopsiaPositivaComCidBenignoViraA2() {
        var c = caso("2", "D440").eventos(List.of(evento("2", "biopsia", "positivo", 40))).build();
        var r = avaliar(c);
        assertThat(r.classe()).isEqualTo(ClasseSwalis.A2);
        assertThat(r.getJustificativa()).isNull(); // justificativa só é gerada em priorizar()
        assertThat(r.oncologico()).isTrue();
    }

    @Test
    void oncologicoComProgressaoViraA1() {
        var c = caso("3", "C189").eventos(List.of(evento("3", "exame_imagem", "progressao", 30))).build();
        assertThat(avaliar(c).classe()).isEqualTo(ClasseSwalis.A1);
    }

    @Test
    void negacaoNoTextoNaoDisparaAlarme() {
        var c = caso("4", "K409").justificativa("Hernia inguinal redutivel. Sem sinais de encarceramento.").build();
        var r = avaliar(c);
        assertThat(r.temSinal(TipoSinal.ALARME_GRAVE)).isFalse();
        assertThat(r.classe()).isEqualTo(ClasseSwalis.D);
    }

    @Test
    void alarmeNoTextoViraA1() {
        var c = caso("5", "K409").justificativa("Hernia inguinal volumosa com episodio de encarceramento ha 2 semanas.").build();
        assertThat(avaliar(c).classe()).isEqualTo(ClasseSwalis.A1);
    }

    @Test
    void dadoFaltandoNaoRebaixaParaD() {
        var c = caso("6", "H251").dor(null).limitacao(null).build();
        var r = avaliar(c);
        assertThat(r.classe()).isEqualTo(ClasseSwalis.C);
        assertThat(r.temSinal(TipoSinal.DADO_INCOMPLETO)).isTrue();
    }

    @Test
    void psRecenteViraB() {
        var c = caso("7", "K801").eventos(List.of(evento("7", "pronto_socorro", "atendido_liberado", 20))).build();
        assertThat(avaliar(c).classe()).isEqualTo(ClasseSwalis.B);
    }

    @Test
    void reguladorNaoConsegueRebaixarOncologicoAbaixoDeA2() {
        var c = caso("8", "C509").build();
        var r = motor.avaliar(c, Pesos.PADRAO, new DecisaoRegulador(ClasseSwalis.D, "teste", null, null));
        assertThat(r.classe()).isEqualTo(ClasseSwalis.A2);
    }

    @Test
    void desistenciaDeNaoOncologicoViraSaidaSugeridaMasOncologicoFica() {
        var naoOnco = caso("9", "K409").observacoes("PCT DESISTIU DO PROCEDIMENTO. SOLICITA RETIRADA DA FILA.").build();
        var onco = caso("10", "C509").observacoes("PCT DESISTIU DO PROCEDIMENTO. SOLICITA RETIRADA DA FILA.").build();
        assertThat(avaliar(naoOnco).getStatus()).isEqualTo(StatusFila.SAIDA_SUGERIDA);
        assertThat(avaliar(onco).getStatus()).isEqualTo(StatusFila.ATIVA);
    }

    @Test
    void regraDeOuroRespeitadaMesmoComAjusteManualQuePromoveNaoUrgente() {
        List<Caso> casos = new ArrayList<>();
        for (int i = 0; i < 30; i++) casos.add(caso("N" + i, "K409").build());
        for (int i = 0; i < 10; i++) casos.add(caso("C" + i, "C509").build());
        // regulador promove um paciente sem urgência para A1: ele sobe, mas os oncológicos sobem junto
        var decisoes = Map.of("N0", new DecisaoRegulador(ClasseSwalis.A1, "pedido do cirurgiao", null, null));
        var fila = motor.priorizar(casos, Pesos.PADRAO, decisoes).stream()
                .filter(r -> r.getStatus() == StatusFila.ATIVA).toList();
        assertThat(GuardRegraDeOuro.respeitada(fila)).isTrue();
        assertThat(fila.getFirst().getCaso().oncologicoPeloCid()).isTrue();
        assertThat(fila.get(10).getCaso().solicitacaoId()).isEqualTo("N0");
    }

    @Test
    void ordemTemPosicoesSequenciaisEJustificativa() {
        var fila = motor.priorizar(List.of(caso("A", "K409").build(), caso("B", "C509").build()), Pesos.PADRAO, Map.of());
        assertThat(fila).extracting(ResultadoPaciente::getPosicao).containsExactly(1, 2);
        assertThat(fila.getFirst().getCaso().solicitacaoId()).isEqualTo("B");
        assertThat(fila.getFirst().getJustificativa()).startsWith("A2");
    }
}
