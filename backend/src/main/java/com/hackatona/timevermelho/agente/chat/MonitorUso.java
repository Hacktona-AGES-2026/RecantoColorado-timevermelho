package com.hackatona.timevermelho.agente.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Acompanha o sistema todo: recusas nos últimos 10 minutos e tokens gastos na última hora. Quando algum dos dois
 * dispara (ataque, bug ou uso fora do normal), escreve um aviso no log, no máximo um a cada 5 minutos.
 */
public class MonitorUso {

    private static final Logger log = LoggerFactory.getLogger(MonitorUso.class);
    private static final Duration JANELA_RECUSAS = Duration.ofMinutes(10);
    private static final Duration JANELA_TOKENS = Duration.ofHours(1);
    private static final Duration ENTRE_AVISOS = Duration.ofMinutes(5);

    private record Gasto(Instant quando, long tokens) {
    }

    private final int alertaRecusas;
    private final long alertaTokens;
    private final Clock clock;
    private final Deque<Instant> recusas = new ArrayDeque<>();
    private final Deque<Gasto> gastos = new ArrayDeque<>();
    private Instant ultimoAviso = Instant.MIN;

    public MonitorUso(int alertaRecusas10Min, long alertaTokensHora, Clock clock) {
        this.alertaRecusas = Math.max(1, alertaRecusas10Min);
        this.alertaTokens = Math.max(1, alertaTokensHora);
        this.clock = clock;
    }

    public synchronized void registrarRecusa() {
        Instant agora = clock.instant();
        recusas.addLast(agora);
        limpar(agora);
        if (recusas.size() >= alertaRecusas) avisar(agora, "muitas recusas: " + recusas.size() + " nos últimos 10 minutos");
    }

    public synchronized void registrarTokens(long tokens) {
        if (tokens <= 0) return;
        Instant agora = clock.instant();
        gastos.addLast(new Gasto(agora, tokens));
        limpar(agora);
        long total = gastos.stream().mapToLong(Gasto::tokens).sum();
        if (total >= alertaTokens) avisar(agora, "gasto alto: " + total + " tokens na última hora");
    }

    public synchronized int recusasUltimos10Min() {
        limpar(clock.instant());
        return recusas.size();
    }

    public synchronized long tokensUltimaHora() {
        limpar(clock.instant());
        return gastos.stream().mapToLong(Gasto::tokens).sum();
    }

    private void limpar(Instant agora) {
        Instant corteRecusas = agora.minus(JANELA_RECUSAS);
        while (!recusas.isEmpty() && recusas.peekFirst().isBefore(corteRecusas)) recusas.pollFirst();
        Instant corteTokens = agora.minus(JANELA_TOKENS);
        while (!gastos.isEmpty() && gastos.peekFirst().quando().isBefore(corteTokens)) gastos.pollFirst();
    }

    private void avisar(Instant agora, String motivo) {
        if (agora.isBefore(ultimoAviso.plus(ENTRE_AVISOS))) return;
        ultimoAviso = agora;
        log.warn("agente: ALERTA de uso: {}", motivo);
    }
}
