package com.hackatona.timevermelho.servico;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hackatona.timevermelho.motor.Caso;
import com.hackatona.timevermelho.motor.DecisaoRegulador;
import com.hackatona.timevermelho.motor.MotorPriorizacao;
import com.hackatona.timevermelho.motor.Pesos;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.StatusFila;
import com.hackatona.timevermelho.motor.TipoSinal;
import com.hackatona.timevermelho.motor.texto.RegrasClinicas;

class ImpactoCalculadoraTest {

    static final LocalDate REF = LocalDate.of(2026, 10, 23);
    final MotorPriorizacao motor = new MotorPriorizacao(RegrasClinicas.carregarPadrao(), REF);

    Caso.CasoBuilder caso(String cid) {
        return Caso.builder().solicitacaoId("S1").pacienteId("P1").nome("Maria").cid(cid).cidFormatado(cid)
                .procedimento("Proc").riscoCirurgico(2).asa(2).dor(1).limitacao("nenhuma")
                .justificativa("Paciente com indicacao cirurgica eletiva, estavel.")
                .dataEntrada(REF.minusDays(100)).diasEmFila(100).prioridadeManual("media")
                .flagOncologico("nao").flagPiora("nao").eventos(List.of());
    }

    ResultadoPaciente resultado(Caso c, Integer posicao) {
        ResultadoPaciente r = motor.avaliar(c, Pesos.PADRAO, DecisaoRegulador.NENHUMA);
        r.setPosicao(posicao);
        return r;
    }

    @Test
    void sinaisNovosSaoSoOsQueNaoExistiamAntes() {
        var antes = resultado(caso("K409").build(), 10);
        var depois = resultado(caso("K409").justificativa("Hernia com encarceramento ha 2 dias, quadro estavel.").build(), 3);
        var novos = ImpactoCalculadora.sinaisNovos(antes, depois);
        assertThat(novos).extracting(s -> s.tipo()).containsExactly(TipoSinal.ALARME_GRAVE);
    }

    @Test
    void semMudancaNaoHaSinaisNovos() {
        var r = resultado(caso("K409").build(), 10);
        assertThat(ImpactoCalculadora.sinaisNovos(r, r)).isEmpty();
    }

    @Test
    void mensagemQuandoSobe() {
        var msg = ImpactoCalculadora.mensagem(resultado(caso("K409").build(), 10), resultado(caso("K409").build(), 3), "motivo X");
        assertThat(msg).isEqualTo("Subiu da posição 10 para 3: motivo X");
    }

    @Test
    void mensagemQuandoDesce() {
        var msg = ImpactoCalculadora.mensagem(resultado(caso("K409").build(), 3), resultado(caso("K409").build(), 10), null);
        assertThat(msg).isEqualTo("Desceu da posição 3 para 10");
    }

    @Test
    void mensagemQuandoPosicaoNaoMuda() {
        var msg = ImpactoCalculadora.mensagem(resultado(caso("K409").build(), 7), resultado(caso("K409").build(), 7), " ");
        assertThat(msg).isEqualTo("Posição mantida (7)");
    }

    @Test
    void mensagemQuandoMudaDeClasse() {
        var antes = resultado(caso("K409").build(), 20);
        var depois = resultado(caso("K409").justificativa("Hernia com encarceramento ha 2 dias, quadro estavel.").build(), 2);
        assertThat(ImpactoCalculadora.mensagem(antes, depois, "alarme")).contains("classe D → A1").endsWith(": alarme");
    }

    @Test
    void mensagemQuandoReguladorConfirmaASaida() {
        var antes = resultado(caso("K409").build(), 12);
        var depois = resultado(caso("K409").build(), null);
        depois.setStatus(StatusFila.SAIDA_CONFIRMADA);
        assertThat(ImpactoCalculadora.mensagem(antes, depois, "confirmou")).startsWith("Saiu da fila (estava na posição 12)");
    }

    @Test
    void saidaSugeridaNaoTiraONumeroDaFila() {
        var antes = resultado(caso("K409").build(), 12);
        var depois = resultado(caso("K409").build(), 12);
        depois.setStatus(StatusFila.SAIDA_SUGERIDA);
        assertThat(ImpactoCalculadora.mensagem(antes, depois, null)).startsWith("Posição mantida (12)");
    }

    @Test
    void mensagemQuandoVoltaParaAFila() {
        var antes = resultado(caso("K409").build(), null);
        antes.setStatus(StatusFila.SAIDA_SUGERIDA);
        var depois = resultado(caso("K409").build(), 15);
        assertThat(ImpactoCalculadora.mensagem(antes, depois, null)).startsWith("Voltou para a fila na posição 15");
    }

    @Test
    void impactoPreencheTodosOsCampos() {
        var antes = resultado(caso("K409").build(), 10);
        var depois = resultado(caso("K409").build(), 4);
        var i = ImpactoCalculadora.impacto(antes, depois, "motivo");
        assertThat(i.solicitacaoId()).isEqualTo("S1");
        assertThat(i.nome()).isEqualTo("Maria");
        assertThat(i.posicaoAntes()).isEqualTo(10);
        assertThat(i.posicaoDepois()).isEqualTo(4);
        assertThat(i.classeAntes()).isEqualTo("D");
        assertThat(i.classeDepois()).isEqualTo("D");
        assertThat(i.statusAntes()).isEqualTo("ATIVA");
        assertThat(i.statusDepois()).isEqualTo("ATIVA");
        assertThat(i.mensagem()).isEqualTo("Subiu da posição 10 para 4: motivo");
    }
}
