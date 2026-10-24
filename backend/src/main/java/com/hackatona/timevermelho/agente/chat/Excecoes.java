package com.hackatona.timevermelho.agente.chat;

/** Exceções do chat que viram resposta HTTP antes de começar a transmitir. */
public final class Excecoes {

    private Excecoes() {}

    /** Agente desligado (configuração, regras ausentes, sem provedor). HTTP 503. */
    public static class AgenteDesligadoException extends RuntimeException {
        public AgenteDesligadoException(String motivo) {
            super(motivo);
        }
    }

    /** Muitas mensagens em pouco tempo. HTTP 429. */
    public static class LimiteExcedidoException extends RuntimeException {
        public LimiteExcedidoException() {
            super("Muitas mensagens em pouco tempo. Aguarde um minuto e tente de novo.");
        }

        public LimiteExcedidoException(String mensagem) {
            super(mensagem);
        }
    }

    /** Mensagem vazia ou grande demais. HTTP 400. */
    public static class MensagemInvalidaException extends RuntimeException {
        public MensagemInvalidaException(String motivo) {
            super(motivo);
        }
    }

    /** O cliente fechou a conexão: para de gerar para não gastar crédito à toa. */
    public static class ClienteDesconectouException extends RuntimeException {
        public ClienteDesconectouException(Throwable causa) {
            super("cliente desconectou", causa);
        }
    }

    /** A resposta do modelo repetiu o System Prompt e foi interrompida. */
    static class RespostaBloqueadaException extends RuntimeException {
        RespostaBloqueadaException() {
            super("resposta bloqueada");
        }
    }
}
