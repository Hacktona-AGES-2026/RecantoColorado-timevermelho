package com.hackatona.timevermelho.agente.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Limite de mensagens por minuto para cada chave (IP, sessão), para não esgotar o crédito das APIs.
 * Janela deslizante em memória; chaves antigas são descartadas de tempos em tempos.
 */
public class LimitadorUso {

    private static final Duration JANELA = Duration.ofMinutes(1);

    private final int maximoPorMinuto;
    private final Clock clock;
    private final ConcurrentMap<String, Deque<Instant>> usos = new ConcurrentHashMap<>();
    private volatile Instant ultimaLimpeza;

    public LimitadorUso(int maximoPorMinuto, Clock clock) {
        this.maximoPorMinuto = Math.max(1, maximoPorMinuto);
        this.clock = clock;
        this.ultimaLimpeza = clock.instant();
    }

    /** Registra um uso. {@code false} se a chave estourou o limite (o uso não é contado). */
    public boolean permitir(String chave) {
        Instant agora = clock.instant();
        limparSeNecessario(agora);
        Deque<Instant> fila = usos.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (fila) {
            Instant corte = agora.minus(JANELA);
            while (!fila.isEmpty() && fila.peekFirst().isBefore(corte)) fila.pollFirst();
            if (fila.size() >= maximoPorMinuto) return false;
            fila.addLast(agora);
            return true;
        }
    }

    private void limparSeNecessario(Instant agora) {
        if (agora.isBefore(ultimaLimpeza.plus(JANELA))) return;
        ultimaLimpeza = agora;
        Instant corte = agora.minus(JANELA);
        usos.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast().isBefore(corte);
            }
        });
    }
}
