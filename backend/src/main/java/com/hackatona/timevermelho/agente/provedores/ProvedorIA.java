package com.hackatona.timevermelho.agente.provedores;

import java.util.List;
import java.util.function.Consumer;

/** Um provedor de IA. O resto do agente não sabe qual deles está respondendo. */
public interface ProvedorIA {

    String nome();

    /** Resposta completa de uma vez. */
    Resposta gerar(List<Mensagem> mensagens, Opcoes opcoes);

    /** Resposta em pedaços; {@code aoReceber} é chamado a cada trecho de texto. */
    Resposta gerarStreaming(List<Mensagem> mensagens, Opcoes opcoes, Consumer<String> aoReceber);

    boolean suportaModeracao();

    /** {@code true} se o texto foi sinalizado como impróprio. */
    boolean moderar(String texto);

    record Opcoes(boolean json, int maxTokens) {

        public static Opcoes texto(int maxTokens) {
            return new Opcoes(false, maxTokens);
        }

        public static Opcoes json(int maxTokens) {
            return new Opcoes(true, maxTokens);
        }
    }

    record Resposta(String texto, long tokensEntrada, long tokensSaida) {
    }
}
