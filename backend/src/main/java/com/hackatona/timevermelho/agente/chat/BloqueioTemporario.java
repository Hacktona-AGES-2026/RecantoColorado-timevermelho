package com.hackatona.timevermelho.agente.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Depois de várias recusas seguidas (fora do escopo, tentativa de burlar, moderação), o mesmo IP fica bloqueado
 * por alguns minutos: quem só está testando limites não gasta o crédito. Pedido de ajuda em emergência de saúde
 * nunca conta como recusa nem é bloqueado (quem decide isso é o {@link ChatService}).
 */
public class BloqueioTemporario {

    private final int recusasParaBloquear;
    private final Duration janela;
    private final Duration bloqueio;
    private final Clock clock;
    private final ConcurrentMap<String, Deque<Instant>> recusas = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> bloqueadosAte = new ConcurrentHashMap<>();

    public BloqueioTemporario(int recusasParaBloquear, Duration janela, Duration bloqueio, Clock clock) {
        this.recusasParaBloquear = Math.max(1, recusasParaBloquear);
        this.janela = janela;
        this.bloqueio = bloqueio;
        this.clock = clock;
    }

    /** Anota uma recusa; se passar do limite na janela, bloqueia a chave. */
    public void registrarRecusa(String chave) {
        Instant agora = clock.instant();
        Deque<Instant> fila = recusas.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (fila) {
            Instant corte = agora.minus(janela);
            while (!fila.isEmpty() && fila.peekFirst().isBefore(corte)) fila.pollFirst();
            fila.addLast(agora);
            if (fila.size() >= recusasParaBloquear) {
                bloqueadosAte.put(chave, agora.plus(bloqueio));
                fila.clear();
            }
        }
    }

    /** Quanto falta de bloqueio para a chave, se ela estiver bloqueada agora. */
    public Optional<Duration> bloqueadoPor(String chave) {
        Instant ate = bloqueadosAte.get(chave);
        if (ate == null) return Optional.empty();
        Instant agora = clock.instant();
        if (!agora.isBefore(ate)) {
            bloqueadosAte.remove(chave, ate);
            return Optional.empty();
        }
        return Optional.of(Duration.between(agora, ate));
    }
}
