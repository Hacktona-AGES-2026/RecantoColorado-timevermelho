package com.hackatona.timevermelho.agente.chat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.hackatona.timevermelho.agente.provedores.Mensagem;

/**
 * Histórico das conversas, guardado só no servidor e separado por sessão (T30). O cliente manda apenas a
 * mensagem nova, nunca o histórico, então ninguém consegue "inventar" falas anteriores do assistente.
 * O identificador é aleatório (128 bits); um identificador desconhecido ou de sessão expirada ganha uma sessão nova.
 */
public class SessoesChat {

    public static final class Sessao {
        private final String id;
        private final List<Mensagem> historico = new ArrayList<>();
        private volatile Instant ultimoUso;

        private Sessao(String id, Instant agora) {
            this.id = id;
            this.ultimoUso = agora;
        }

        public String id() {
            return id;
        }

        /** Cópia das últimas mensagens (no máximo {@code max}). */
        public synchronized List<Mensagem> ultimas(int max) {
            int de = Math.max(0, historico.size() - max);
            return List.copyOf(historico.subList(de, historico.size()));
        }

        synchronized void registrar(String usuario, String assistente, int maxHistorico) {
            historico.add(Mensagem.usuario(usuario));
            historico.add(Mensagem.assistente(assistente));
            while (historico.size() > maxHistorico) historico.removeFirst();
        }
    }

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxSessoes;
    private final int maxHistorico;
    private final Clock clock;

    public SessoesChat(Duration ttl, int maxSessoes, int maxHistorico, Clock clock) {
        this.ttl = ttl;
        this.maxSessoes = Math.max(1, maxSessoes);
        this.maxHistorico = Math.max(2, maxHistorico);
        this.clock = clock;
    }

    /** Devolve a sessão do identificador (se existir e estiver viva) ou cria uma nova. */
    public Sessao obter(String idInformado) {
        Instant agora = clock.instant();
        removerExpiradas(agora);
        if (idInformado != null) {
            Sessao s = sessoes.get(idInformado);
            if (s != null) {
                s.ultimoUso = agora;
                return s;
            }
        }
        if (sessoes.size() >= maxSessoes) removerMaisAntiga();
        byte[] bytes = new byte[16];
        ALEATORIO.nextBytes(bytes);
        Sessao nova = new Sessao(HexFormat.of().formatHex(bytes), agora);
        sessoes.put(nova.id, nova);
        return nova;
    }

    public void registrar(Sessao sessao, String usuario, String assistente) {
        sessao.registrar(usuario, assistente, maxHistorico);
        sessao.ultimoUso = clock.instant();
    }

    public int total() {
        return sessoes.size();
    }

    private void removerExpiradas(Instant agora) {
        Instant corte = agora.minus(ttl);
        sessoes.values().removeIf(s -> s.ultimoUso.isBefore(corte));
    }

    private void removerMaisAntiga() {
        sessoes.values().stream().min(java.util.Comparator.comparing(s -> s.ultimoUso))
                .ifPresent(s -> sessoes.remove(s.id));
    }
}
