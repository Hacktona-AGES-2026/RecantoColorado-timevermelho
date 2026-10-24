package com.hackatona.timevermelho.agente.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.hackatona.timevermelho.agente.provedores.RelogioFalso;

/** T21: limite de mensagens por minuto, para não esgotar o crédito. */
class LimitadorUsoTest {

    final RelogioFalso relogio = new RelogioFalso();

    @Test
    void bloqueiaDepoisDoLimiteEDevolveNoMinutoSeguinte() {
        var limitador = new LimitadorUso(3, relogio);
        assertThat(limitador.permitir("ip:1")).isTrue();
        assertThat(limitador.permitir("ip:1")).isTrue();
        assertThat(limitador.permitir("ip:1")).isTrue();
        assertThat(limitador.permitir("ip:1")).isFalse();
        assertThat(limitador.permitir("ip:1")).isFalse();

        relogio.avancar(Duration.ofSeconds(61));
        assertThat(limitador.permitir("ip:1")).isTrue();
    }

    @Test
    void cadaChaveTemOSeuProprioLimite() {
        var limitador = new LimitadorUso(1, relogio);
        assertThat(limitador.permitir("ip:1")).isTrue();
        assertThat(limitador.permitir("ip:1")).isFalse();
        assertThat(limitador.permitir("ip:2")).isTrue();
        assertThat(limitador.permitir("sessao:abc")).isTrue();
    }

    @Test
    void usoRecusadoNaoContaParaAJanela() {
        var limitador = new LimitadorUso(2, relogio);
        limitador.permitir("k");
        relogio.avancar(Duration.ofSeconds(30));
        limitador.permitir("k");
        for (int i = 0; i < 10; i++) assertThat(limitador.permitir("k")).isFalse();
        relogio.avancar(Duration.ofSeconds(31)); // o 1º uso saiu da janela de 1 minuto
        assertThat(limitador.permitir("k")).isTrue();
        assertThat(limitador.permitir("k")).isFalse();
    }

    @Test
    void limiteInvalidoViraNoMinimoUm() {
        var limitador = new LimitadorUso(0, relogio);
        assertThat(limitador.permitir("k")).isTrue();
        assertThat(limitador.permitir("k")).isFalse();
    }
}
