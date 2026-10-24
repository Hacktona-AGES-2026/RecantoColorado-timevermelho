package com.hackatona.timevermelho.agente.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.hackatona.timevermelho.agente.provedores.RelogioFalso;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** T43: bloqueio temporário depois de recusas seguidas e alerta de uso fora do normal. */
class BloqueioEMonitorTest {

    final RelogioFalso relogio = new RelogioFalso();

    @Test
    void depoisDeVariasRecusasOIpFicaBloqueadoPorUmTempoEDepoisVolta() {
        var bloqueio = new BloqueioTemporario(3, Duration.ofMinutes(10), Duration.ofMinutes(5), relogio);
        bloqueio.registrarRecusa("ip:1");
        bloqueio.registrarRecusa("ip:1");
        assertThat(bloqueio.bloqueadoPor("ip:1")).isEmpty();

        bloqueio.registrarRecusa("ip:1");
        assertThat(bloqueio.bloqueadoPor("ip:1")).isPresent();
        assertThat(bloqueio.bloqueadoPor("ip:1").get()).isEqualTo(Duration.ofMinutes(5));

        relogio.avancar(Duration.ofMinutes(2));
        assertThat(bloqueio.bloqueadoPor("ip:1").get()).isEqualTo(Duration.ofMinutes(3));

        relogio.avancar(Duration.ofMinutes(4));
        assertThat(bloqueio.bloqueadoPor("ip:1")).isEmpty();
    }

    @Test
    void recusasForaDaJanelaNaoSomam() {
        var bloqueio = new BloqueioTemporario(3, Duration.ofMinutes(10), Duration.ofMinutes(5), relogio);
        bloqueio.registrarRecusa("ip:1");
        bloqueio.registrarRecusa("ip:1");
        relogio.avancar(Duration.ofMinutes(11));
        bloqueio.registrarRecusa("ip:1");
        assertThat(bloqueio.bloqueadoPor("ip:1")).isEmpty();
    }

    @Test
    void oBloqueioEhPorChaveENaoAfetaOutrosIps() {
        var bloqueio = new BloqueioTemporario(1, Duration.ofMinutes(10), Duration.ofMinutes(5), relogio);
        bloqueio.registrarRecusa("ip:1");
        assertThat(bloqueio.bloqueadoPor("ip:1")).isPresent();
        assertThat(bloqueio.bloqueadoPor("ip:2")).isEmpty();
    }

    @Test
    void depoisDeDesbloquearPrecisaDeNovasRecusasParaBloquearDeNovo() {
        var bloqueio = new BloqueioTemporario(2, Duration.ofMinutes(10), Duration.ofMinutes(5), relogio);
        bloqueio.registrarRecusa("ip:1");
        bloqueio.registrarRecusa("ip:1");
        relogio.avancar(Duration.ofMinutes(6));
        assertThat(bloqueio.bloqueadoPor("ip:1")).isEmpty();
        bloqueio.registrarRecusa("ip:1");
        assertThat(bloqueio.bloqueadoPor("ip:1")).isEmpty();
    }

    // ---------- monitor ----------

    @Test
    void monitorContaRecusasDosUltimos10MinutosETokensDaUltimaHora() {
        var monitor = new MonitorUso(100, 1_000_000, relogio);
        monitor.registrarRecusa();
        monitor.registrarRecusa();
        monitor.registrarTokens(500);
        monitor.registrarTokens(0);
        monitor.registrarTokens(-3);
        assertThat(monitor.recusasUltimos10Min()).isEqualTo(2);
        assertThat(monitor.tokensUltimaHora()).isEqualTo(500);

        relogio.avancar(Duration.ofMinutes(11));
        assertThat(monitor.recusasUltimos10Min()).isZero();
        assertThat(monitor.tokensUltimaHora()).isEqualTo(500);

        relogio.avancar(Duration.ofMinutes(50));
        assertThat(monitor.tokensUltimaHora()).isZero();
    }

    private ListAppender<ILoggingEvent> escutarLogs() {
        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(MonitorUso.class)).addAppender(logs);
        return logs;
    }

    @Test
    void monitorAvisaNoLogQuandoORitmoDeRecusasDisparaEUmAvisoACada5Minutos() {
        var logs = escutarLogs();
        try {
            var monitor = new MonitorUso(3, 1_000_000, relogio);
            for (int i = 0; i < 10; i++) monitor.registrarRecusa();
            assertThat(logs.list).hasSize(1);
            assertThat(logs.list.getFirst().getFormattedMessage()).contains("ALERTA").contains("muitas recusas");

            relogio.avancar(Duration.ofMinutes(6));
            monitor.registrarRecusa();
            assertThat(logs.list).hasSize(2);
        } finally {
            ((Logger) LoggerFactory.getLogger(MonitorUso.class)).detachAppender(logs);
        }
    }

    @Test
    void monitorAvisaQuandoOGastoDeTokensDisparaSemMostrarConteudoNenhum() {
        var logs = escutarLogs();
        try {
            var monitor = new MonitorUso(100, 1000, relogio);
            monitor.registrarTokens(600);
            assertThat(logs.list).isEmpty();
            monitor.registrarTokens(600);
            assertThat(logs.list).hasSize(1);
            assertThat(logs.list.getFirst().getFormattedMessage()).contains("gasto alto").contains("1200 tokens");
        } finally {
            ((Logger) LoggerFactory.getLogger(MonitorUso.class)).detachAppender(logs);
        }
    }
}
