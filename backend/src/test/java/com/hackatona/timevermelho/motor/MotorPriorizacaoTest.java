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
    void necroseAssepticaNaoEhSepse() {
        var c = caso("11", "M169").justificativa("Necrose asseptica de cabeca femoral direita, dor intensa.").build();
        assertThat(avaliar(c).temSinal(TipoSinal.ALARME_GRAVE)).isFalse();
        var sepse = caso("12", "N201").justificativa("ITU de repeticao com sepse urinaria ha 1 mes.").build();
        assertThat(avaliar(sepse).classe()).isEqualTo(ClasseSwalis.A1);
    }

    @Test
    void doencaCoronariaGraveNaoFicaEmD() {
        // dor baixa e limitação leve, mas o texto descreve doença grave
        var tronco = caso("13", "I251").justificativa("DOENCA ARTERIAL CORONARIANA MULTIARTERIAL. ANGINA ESTAVEL CCS II. CATE: TRONCO 80%.").build();
        assertThat(avaliar(tronco).classe()).isEqualTo(ClasseSwalis.A1);
        var tri = caso("14", "I251").justificativa("DAC triarterial, angina CCS III, FE 36%. Cateterismo: lesoes >70% em DA, CX e CD.").build();
        assertThat(avaliar(tri).classe()).isEqualTo(ClasseSwalis.B);
        var feNormal = caso("15", "I251").justificativa("Cardiopatia isquemica estavel, FE 61%, angina CCS I com medicacao.").build();
        assertThat(avaliar(feNormal).classe()).isEqualTo(ClasseSwalis.D);
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
    void saidaSugeridaContinuaNaFilaAteORegularConfirmar() {
        String desistiu = "PCT DESISTIU DO PROCEDIMENTO. SOLICITA RETIRADA DA FILA.";
        var naoOnco = caso("9", "K409").observacoes(desistiu).build();
        var onco = caso("10", "C509").observacoes(desistiu).build();
        var fila = motor.priorizar(List.of(naoOnco, onco), Pesos.PADRAO, Map.of());
        // ninguém sai sem decisão humana: os dois têm posição e aviso de reavaliação
        assertThat(fila).allSatisfy(r -> {
            assertThat(r.getStatus()).isEqualTo(StatusFila.SAIDA_SUGERIDA);
            assertThat(r.getPosicao()).isNotNull();
            assertThat(r.getJustificativa()).contains("saída sugerida aguardando o regulador");
            assertThat(r.getReavaliacoes()).extracting(Reavaliacao::tipo).contains(TipoReavaliacao.CONFIRMAR_SAIDA);
        });
        // confirmou: sai da fila; recusou: volta a ser paciente comum da fila
        var confirmada = motor.priorizar(List.of(naoOnco), Pesos.PADRAO,
                Map.of("9", new DecisaoRegulador(null, null, true, "confirmado por telefone")));
        assertThat(confirmada.getFirst().getStatus()).isEqualTo(StatusFila.SAIDA_CONFIRMADA);
        assertThat(confirmada.getFirst().getPosicao()).isNull();
        var recusada = motor.priorizar(List.of(naoOnco), Pesos.PADRAO,
                Map.of("9", new DecisaoRegulador(null, null, false, "paciente quer operar")));
        assertThat(recusada.getFirst().getStatus()).isEqualTo(StatusFila.ATIVA);
        assertThat(recusada.getFirst().getPosicao()).isEqualTo(1);
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
    void eventoNovoMaisPromocaoNaoPassaNaFrenteDeCancer() {
        // cenário que quebrava o verificador oficial: paciente calmo no dataset recebe piora registrada
        // na hora (que o verificador não enxerga) e o regulador o promove para A1
        var psNovo = new Evento("EVT-NOVO-1", "N0", REF, "pronto_socorro", "observacao", null, "PS agora", "FilaVIVA", true);
        List<Caso> casos = new ArrayList<>();
        casos.add(caso("N0", "H250").eventos(List.of(psNovo)).build());
        for (int i = 0; i < 5; i++) casos.add(caso("C" + i, "C509").build());
        var decisoes = Map.of("N0", new DecisaoRegulador(ClasseSwalis.A1, "cirurgiao pediu", null, null));
        var fila = motor.priorizar(casos, Pesos.PADRAO, decisoes);
        var n0 = fila.stream().filter(r -> r.getCaso().solicitacaoId().equals("N0")).findFirst().orElseThrow();
        assertThat(n0.classe()).isEqualTo(ClasseSwalis.A1);
        assertThat(n0.isSemUrgencia()).isTrue(); // pela definição oficial, que só vê o dataset
        assertThat(n0.getPosicao()).isEqualTo(6); // abaixo dos 5 oncológicos
        assertThat(GuardRegraDeOuro.respeitada(fila)).isTrue();

        // com o mesmo PS vindo do dataset, o verificador também vê a urgência: aí ele pode passar na frente
        var psDataset = new Evento("EVT-000001", "N0", REF, "pronto_socorro", "observacao", null, "PS", "UPA", false);
        casos.set(0, caso("N0", "H250").eventos(List.of(psDataset)).build());
        var fila2 = motor.priorizar(casos, Pesos.PADRAO, decisoes);
        assertThat(fila2.getFirst().getCaso().solicitacaoId()).isEqualTo("N0");
        assertThat(GuardRegraDeOuro.respeitada(fila2)).isTrue();
    }

    @Test
    void ordemTemPosicoesSequenciaisEJustificativa() {
        var fila = motor.priorizar(List.of(caso("A", "K409").build(), caso("B", "C509").build()), Pesos.PADRAO, Map.of());
        assertThat(fila).extracting(ResultadoPaciente::getPosicao).containsExactly(1, 2);
        assertThat(fila.getFirst().getCaso().solicitacaoId()).isEqualTo("B");
        assertThat(fila.getFirst().getJustificativa()).startsWith("A2");
    }
}
